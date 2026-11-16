package com.github.tvbox.osc.util.cast;

import android.os.Handler;
import android.os.Looper;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 轻量 DLNA/UPnP 投屏实现（不依赖第三方库）：
 * 1. SSDP M-SEARCH 搜索局域网内的 MediaRenderer（电视/盒子）；
 * 2. 拉取设备描述 XML，解析出 AVTransport 控制地址；
 * 3. 通过 SOAP 调用 SetAVTransportURI + Play 把地址投到电视上播放。
 */
public class DlnaCastManager {

    private static final String SSDP_ADDR = "239.255.255.250";
    private static final int SSDP_PORT = 1900;
    private static final String SEARCH_TARGET = "urn:schemas-upnp-org:device:MediaRenderer:1";
    private static final String M_SEARCH =
            "M-SEARCH * HTTP/1.1\r\n" +
                    "HOST: " + SSDP_ADDR + ":" + SSDP_PORT + "\r\n" +
                    "MAN: \"ssdp:discover\"\r\n" +
                    "MX: 2\r\n" +
                    "ST: " + SEARCH_TARGET + "\r\n" +
                    "\r\n";

    public static class Device {
        public final String name;
        public final String location;
        public final String controlUrl;

        public Device(String name, String location, String controlUrl) {
            this.name = name;
            this.location = location;
            this.controlUrl = controlUrl;
        }
    }

    public interface DiscoveryListener {
        void onDeviceFound(Device device);

        void onSearchFinished();

        void onError(String message);
    }

    public interface CastListener {
        void onResult(boolean success, String message);
    }

    private static volatile DlnaCastManager sInstance;

    public static DlnaCastManager get() {
        if (sInstance == null) {
            synchronized (DlnaCastManager.class) {
                if (sInstance == null) sInstance = new DlnaCastManager();
            }
        }
        return sInstance;
    }

    private final ExecutorService mPool = Executors.newCachedThreadPool();
    private final Handler mMain = new Handler(Looper.getMainLooper());
    private final AtomicBoolean mSearching = new AtomicBoolean(false);
    private volatile DatagramSocket mSearchSocket = null;

    /**
     * 开始搜索，回调在主线程。
     */
    public void search(int timeoutMs, DiscoveryListener listener) {
        cancel();
        mSearching.set(true);
        mPool.execute(() -> {
            Map<String, String> locations = new LinkedHashMap<>();
            DatagramSocket socket = null;
            try {
                socket = new DatagramSocket();
                mSearchSocket = socket;
                socket.setSoTimeout(600);
                byte[] req = M_SEARCH.getBytes(StandardCharsets.UTF_8);
                InetAddress group = InetAddress.getByName(SSDP_ADDR);
                for (int i = 0; i < 3 && mSearching.get(); i++) {
                    socket.send(new DatagramPacket(req, req.length, group, SSDP_PORT));
                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException ignored) {
                    }
                }
                long deadline = System.currentTimeMillis() + timeoutMs;
                byte[] buf = new byte[4096];
                while (System.currentTimeMillis() < deadline && mSearching.get()) {
                    DatagramPacket packet = new DatagramPacket(buf, buf.length);
                    try {
                        socket.receive(packet);
                    } catch (SocketTimeoutException e) {
                        continue;
                    } catch (IOException e) {
                        break;
                    }
                    String resp = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
                    String location = header(resp, "location");
                    if (location != null && !location.isEmpty()) {
                        locations.put(location, location);
                    }
                }
            } catch (Exception e) {
                final String msg = e.getMessage() == null ? e.toString() : e.getMessage();
                mMain.post(() -> listener.onError(msg));
            } finally {
                mSearchSocket = null;
                if (socket != null) socket.close();
            }

            List<Device> devices = new ArrayList<>();
            for (String location : locations.keySet()) {
                if (!mSearching.get()) break;
                Device device = resolve(location);
                if (device != null) devices.add(device);
            }
            mSearching.set(false);
            mMain.post(() -> {
                for (Device device : devices) listener.onDeviceFound(device);
                listener.onSearchFinished();
            });
        });
    }

    public void cancel() {
        mSearching.set(false);
        DatagramSocket socket = mSearchSocket;
        mSearchSocket = null;
        if (socket != null) socket.close();
    }

    /**
     * 投屏：先 SetAVTransportURI，再 Play，回调在主线程。
     */
    public void cast(Device device, String url, String title, CastListener listener) {
        mPool.execute(() -> {
            try {
                String metadata = buildDidl(title, url);
                String setUri = "<InstanceID>0</InstanceID>"
                        + "<CurrentURI>" + escapeXml(url) + "</CurrentURI>"
                        + "<CurrentURIMetaData>" + escapeXml(metadata) + "</CurrentURIMetaData>";
                soap(device.controlUrl, "SetAVTransportURI", setUri);
                String play = "<InstanceID>0</InstanceID><Speed>1</Speed>";
                soap(device.controlUrl, "Play", play);
                mMain.post(() -> listener.onResult(true, "已投屏到 " + device.name));
            } catch (Exception e) {
                final String msg = e.getMessage() == null ? e.toString() : e.getMessage();
                mMain.post(() -> listener.onResult(false, "投屏失败：" + msg));
            }
        });
    }

    // ===================== 内部实现 =====================

    private Device resolve(String location) {
        try {
            String xml = httpGet(location);
            String name = null;
            String controlUrl = null;
            boolean inService = false;
            String serviceType = null;
            String serviceControl = null;

            XmlPullParser parser = Xml.newPullParser();
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
            parser.setInput(new StringReader(xml));
            int event = parser.getEventType();
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    String tag = parser.getName();
                    if ("friendlyName".equals(tag) && name == null) {
                        name = safeText(parser);
                    } else if ("service".equals(tag)) {
                        inService = true;
                        serviceType = null;
                        serviceControl = null;
                    } else if (inService && "serviceType".equals(tag)) {
                        serviceType = safeText(parser);
                    } else if (inService && "controlURL".equals(tag)) {
                        serviceControl = safeText(parser);
                    }
                } else if (event == XmlPullParser.END_TAG) {
                    if ("service".equals(parser.getName()) && inService) {
                        inService = false;
                        if (serviceType != null && serviceType.contains(":service:AVTransport:")
                                && serviceControl != null && !serviceControl.isEmpty()) {
                            controlUrl = serviceControl;
                        }
                    }
                }
                event = parser.next();
            }
            if (controlUrl == null) return null;
            String absolute = new URL(new URL(location), controlUrl).toString();
            return new Device(name == null || name.isEmpty() ? location : name, location, absolute);
        } catch (Exception e) {
            return null;
        }
    }

    private String safeText(XmlPullParser parser) {
        try {
            String text = parser.nextText();
            return text == null ? "" : text.trim();
        } catch (Exception e) {
            return "";
        }
    }

    private String httpGet(String url) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(3000);
        conn.setReadTimeout(3000);
        conn.setRequestProperty("Connection", "close");
        try (InputStream in = conn.getInputStream()) {
            return readAll(in);
        } finally {
            conn.disconnect();
        }
    }

    private String soap(String controlUrl, String action, String body) throws IOException {
        String service = "urn:schemas-upnp-org:service:AVTransport:1";
        String envelope = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\r\n"
                + "<s:Envelope s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\" "
                + "xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\">"
                + "<s:Body><u:" + action + " xmlns:u=\"" + service + "\">"
                + body
                + "</u:" + action + "></s:Body></s:Envelope>";

        HttpURLConnection conn = (HttpURLConnection) new URL(controlUrl).openConnection();
        conn.setConnectTimeout(4000);
        conn.setReadTimeout(6000);
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "text/xml; charset=\"utf-8\"");
        conn.setRequestProperty("SOAPACTION", "\"" + service + "#" + action + "\"");
        conn.setRequestProperty("Connection", "close");
        byte[] data = envelope.getBytes(StandardCharsets.UTF_8);
        conn.setFixedLengthStreamingMode(data.length);
        try (OutputStream out = conn.getOutputStream()) {
            out.write(data);
        }
        int code = conn.getResponseCode();
        InputStream in = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
        String resp = in == null ? "" : readAll(in);
        if (in != null) in.close();
        conn.disconnect();
        if (code >= 400) {
            throw new IOException("HTTP " + code + (resp.isEmpty() ? "" : " " + resp));
        }
        return resp;
    }

    private String buildDidl(String title, String url) {
        String name = title == null || title.isEmpty() ? "投屏" : title;
        return "<DIDL-Lite xmlns=\"urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/\" "
                + "xmlns:dc=\"http://purl.org/dc/elements/1.1/\" "
                + "xmlns:upnp=\"urn:schemas-upnp-org:metadata-1-0/upnp/\">"
                + "<item id=\"0\" parentID=\"-1\" restricted=\"1\">"
                + "<dc:title>" + escapeXml(name) + "</dc:title>"
                + "<upnp:class>object.item.videoItem</upnp:class>"
                + "<res protocolInfo=\"http-get:*:video/*:*\">" + escapeXml(url) + "</res>"
                + "</item></DIDL-Lite>";
    }

    private static String header(String response, String key) {
        for (String line : response.split("\r\n")) {
            int idx = line.indexOf(':');
            if (idx > 0 && line.substring(0, idx).trim().toLowerCase(Locale.ROOT).equals(key)) {
                return line.substring(idx + 1).trim();
            }
        }
        return null;
    }

    private static String readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int len;
        while ((len = in.read(buf)) != -1) {
            out.write(buf, 0, len);
        }
        return out.toString("UTF-8");
    }

    private static String escapeXml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
