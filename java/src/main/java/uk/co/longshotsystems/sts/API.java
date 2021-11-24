package uk.co.longshotsystems.sts;

import com.google.gson.Gson;
import com.neovisionaries.ws.client.WebSocket;
import com.neovisionaries.ws.client.WebSocketAdapter;
import com.neovisionaries.ws.client.WebSocketFactory;
import com.neovisionaries.ws.client.WebSocketFrame;
import io.swagger.client.model.*;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;

import java.util.List;
import java.util.UUID;

public class API {
    public AuthResponse auth(String user, String pass) throws Exception {
        AuthRequest req = new AuthRequest();
        req.setUsername(user);
        req.setPassword(pass);

        return doPost("auth", req, AuthResponse.class);
    }

    private final String url;
    private final HttpClient client;
    private final Gson gson;

    public API(String u) {
        url = u;
        client = HttpClientBuilder.create().build();
        gson = JSON.createGson();
    }

    public BetAdviceResponse advice(BetAdviceRequest req) throws Exception {
        return doPost("advice", req, BetAdviceResponse.class);
    }

    public BetAdviceResponse status(String betId) throws Exception {
        return doGet("advice/" + betId, BetAdviceResponse.class);
    }

    public ClientTradingDecisionResponse confirm(String betId, ClientTradingDecision req) throws Exception {
        return doPost("advice/" + betId + "/confirmation", req, ClientTradingDecisionResponse.class);
    }

    public BetSettlementResponse settlement(List<BetSettlement> req) throws Exception {
        return doPost("settlement", req, BetSettlementResponse.class);
    }

    private <Req, Resp> Resp doPost(String endpoint, Req req, Class<Resp> clazz) throws Exception {
        HttpPost post = new HttpPost(buildUrl(endpoint));
        StringEntity reqData = new StringEntity(gson.toJson(req));
        post.setEntity(reqData);
        post.setHeader("Content-type", "application/json");

        return doRequest(post, clazz);
    }

    public void odds(SocketCallback callback, UUID token) throws Exception {
        String url = "ws://" + this.url + "odds?token=" + token.toString();
        WebSocket ws = new WebSocketFactory().createSocket(url);

        ws.addListener(new WebSocketAdapter() {
            public void onTextMessage(WebSocket websocket, String message) {
                WSMessage m = gson.fromJson(message, WSMessage.class);

                // If we receive a heartbeat we're required to send one back with the same ID.
                Heartbeat hb = m.getHeartbeat();
                if (hb != null) {
                    sendHb(ws, hb.getId());
                } else {
                    callback.onMessage(m);
                }
            }

            @Override
            public void onCloseFrame(WebSocket websocket, WebSocketFrame frame) throws Exception {
                callback.onClosed();
            }
        });

        ws.connect();
    }

    private String buildUrl(String endpoint) {
        return "http://" + url + endpoint;
    }

    private <Resp> Resp doGet(String endpoint, Class<Resp> clazz) throws Exception {
        HttpGet get = new HttpGet(buildUrl(endpoint));
        return doRequest(get, clazz);
    }

    private <Resp> Resp doRequest(HttpUriRequest req, Class<Resp> clazz) throws Exception {
        HttpResponse response = client.execute(req);

        HttpEntity entity = response.getEntity();
        String result = EntityUtils.toString(entity);

        var code = response.getStatusLine().getStatusCode();
        if (code != 200) {
            ErrorResponse err = gson.fromJson(result, ErrorResponse.class);
            throw new APIError(code, err);
        }

        return gson.fromJson(result, clazz);
    }

    public interface SocketCallback {
        void onMessage(WSMessage m);

        void onClosed();
    }

    private void sendHb(WebSocket ws, int id) {
        WSMessage msg = new WSMessage();
        Heartbeat hb = new Heartbeat();
        hb.setId(id);
        msg.setHeartbeat(hb);

        ws.sendText(gson.toJson(msg));
    }

    public static class APIError extends Exception {
        public int code;
        public ErrorResponse error;

        public APIError(int c, ErrorResponse e) {
            code = c;
            error = e;
        }
    }
}
