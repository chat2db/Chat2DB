package ai.chat2db.server.web.api.ws;

import ai.chat2db.server.domain.repository.Dbutils;
import ai.chat2db.server.tools.base.wrapper.result.ActionResult;
import ai.chat2db.server.tools.base.wrapper.result.ListResult;
import ai.chat2db.server.tools.common.model.Context;
import ai.chat2db.server.tools.common.model.LoginUser;
import ai.chat2db.server.tools.common.util.ContextUtils;
import ai.chat2db.server.web.api.controller.rdb.request.DmlRequest;
import ai.chat2db.server.web.api.controller.rdb.vo.ExecuteResultVO;
import ai.chat2db.server.web.api.util.ApplicationContextUtil;
import ai.chat2db.spi.sql.Chat2DBContext;
import ai.chat2db.spi.sql.ConnectInfo;
import com.alibaba.fastjson2.JSONObject;
import com.jcraft.jsch.JSchException;
import jakarta.websocket.*;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.context.support.SpringBeanAutowiringSupport;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
@ServerEndpoint("/api/ws/{token}")
public class WsServer {
    private Session session;

    private static final AtomicInteger OnlineCount = new AtomicInteger(0);

    // The thread-safe Set of the concurrent package is used to store the Session object corresponding to each client.
    private static CopyOnWriteArraySet<Session> SessionSet = new CopyOnWriteArraySet<Session>();

    private static int num = 0;

    private Timer timer = new Timer();


    private Map<String, ConnectInfo> connectInfoMap = new ConcurrentHashMap<>();


    private LoginUser loginUser;

    private WsService wsService;

    /**
     * Method called when connection is established successfully
     */
    @OnOpen
    public void onOpen(Session session, @PathParam("token") String token) throws IOException {
        SessionSet.add(session);
        this.session = session;
        int cnt = OnlineCount.incrementAndGet(); // Add 1 to the online number
        log.info("There are connections added, and the current number of connections is: {}", cnt);

        heartBeat(session);
        this.wsService = ApplicationContextUtil.getBean(WsService.class);
        Dbutils.setSession();
        this.loginUser = wsService.doLogin(token);
        if (this.loginUser == null) {
            ActionResult actionResult = new ActionResult();
            actionResult.setSuccess(false);
            actionResult.setErrorCode("LOGIN_FAIL");
            WsResult wsMessage = new WsResult();
            wsMessage.setActionType(WsMessage.ActionType.OPEN_SESSION);
            wsMessage.setUuid(token);
            wsMessage.setMessage(actionResult);
            SendMessage(this.session, wsMessage);
            onClose();
        }else {
            ActionResult actionResult = new ActionResult();
            actionResult.setSuccess(true);
            WsResult wsMessage = new WsResult();
            wsMessage.setActionType(WsMessage.ActionType.OPEN_SESSION);
            wsMessage.setUuid(token);
            wsMessage.setMessage(actionResult);
            SendMessage(this.session, wsMessage);
        }
        Dbutils.removeSession();
    }


    /**
     * Method called on connection close
     */
    @OnClose
    public void onClose() throws IOException {
        if (SessionSet.contains(session)) {
            SessionSet.remove(this.session);
            session.close();
            for (Map.Entry<String, ConnectInfo> entry : connectInfoMap.entrySet()) {
                ConnectInfo connectInfo = entry.getValue();
                if (connectInfo != null) {
                    Connection connection = connectInfo.getConnection();
                    try {
                        if (connection != null && !connection.isClosed()) {
                            connection.close();
                        }
                    } catch (SQLException e) {
                        log.error("close connection error", e);
                    }

                    com.jcraft.jsch.Session session = connectInfo.getSession();
                    if (session != null && session.isConnected() && connectInfo.getSsh() != null
                            && connectInfo.getSsh().isUse()) {
                        try {
                            session.delPortForwardingL(Integer.parseInt(connectInfo.getSsh().getLocalPort()));
                        } catch (JSchException e) {
                        }
                    }
                }
            }
            int cnt = OnlineCount.decrementAndGet();
            log.info("A connection was closed, session:{},{}", session, this);
            log.info("A connection is closed, the current number of connections is: {}", cnt);
        }
    }

    /**
     * Method called after receiving client message
     *
     * @param message message sent by the client
     */
    @OnMessage(maxMessageSize = 1024000)
    public void onMessage(String message, Session session) {
        CompletableFuture.runAsync(() -> {
            WsMessage wsMessage = JSONObject.parseObject(message, WsMessage.class);
            // Process your messages here
            try {
                String actionType = wsMessage.getActionType();
                if (WsMessage.ActionType.PING.equalsIgnoreCase(actionType)) {
                    WsResult wsResult = new WsResult();
                    ActionResult actionResult = new ActionResult();
                    actionResult.setSuccess(true);
                    wsResult.setActionType(WsMessage.ActionType.PING);
                    wsResult.setUuid(wsMessage.getUuid());
                    wsResult.setMessage(actionResult);
                    SendMessage(session, wsResult);
                    timer.cancel();
                    heartBeat(session);
                } else {
                    ContextUtils.setContext(Context.builder()
                            .loginUser(loginUser)
                            .build());
                    Dbutils.setSession();
                    JSONObject jsonObject = wsMessage.getMessage();
                    Long dataSourceId = jsonObject.getLong("dataSourceId");
                    String databaseName = jsonObject.getString("databaseName");
                    String schemaName = jsonObject.getString("schemaName");
                    Long consoleId = jsonObject.getLong("consoleId");
                    String key = connectInfoKey(dataSourceId, databaseName, schemaName, consoleId);
                    ConnectInfo connectInfo = connectInfoMap.get(key);
                    if (connectInfo == null) {
                        connectInfo = wsService.toInfo(dataSourceId, databaseName, consoleId, schemaName);
                        connectInfoMap.put(key, connectInfo);
                    }
                    Chat2DBContext.putContext(connectInfo);
                    if (WsMessage.ActionType.EXECUTE.equalsIgnoreCase(actionType)) {
                        DmlRequest request = jsonObject.toJavaObject(DmlRequest.class);
                        ListResult<ExecuteResultVO> result = wsService.execute(request);
                        WsResult resultMessage = new WsResult();
                        resultMessage.setUuid(wsMessage.getUuid());
                        resultMessage.setActionType(wsMessage.getActionType());
                        resultMessage.setMessage(result);
                        SendMessage(session, resultMessage);
                    }
                }
            } catch (Exception e) {
                WsResult wsResult = new WsResult();
                ActionResult actionResult = new ActionResult();
                actionResult.setSuccess(false);
                actionResult.setErrorCode(e.getMessage());
                wsResult.setActionType(WsMessage.ActionType.ERROR);
                wsResult.setUuid(wsMessage.getUuid());
                wsResult.setMessage(actionResult);
                SendMessage(session, wsResult);
            } finally {
                Chat2DBContext.removeContext();
                ContextUtils.removeContext();
                Dbutils.removeSession();
            }
        });

    }


    private String connectInfoKey(Long dataSourceId, String databaseName, String schemaName, Long consoleId) {
        return dataSourceId + "_" + databaseName + "_" + schemaName + "_" + consoleId;
    }


    /**
     * An error occurred
     *
     * @param session
     * @param error
     */
    @OnError
    public void onError(Session session, Throwable error) {
        log.error("An error occurred:{}，Session ID： {}", error.getMessage(), session.getId(), error);
    }

    /**
     * heartbeat
     *
     * @param session
     */
    private void heartBeat(Session session) {
        timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                try {
                    onClose();
                } catch (IOException e) {
                    log.error("Error sending message：{}", e.getMessage(), e);
                }
            }
        }, 600000);
    }

    /**
     * Sending a message, practice shows that every time the browser refreshes, the session will change.
     *
     * @param session
     * @param wsResult
     */
    public static void SendMessage(Session session, WsResult wsResult) {
        try {
            if (session.isOpen()) {
                session.getBasicRemote().sendText(JSONObject.toJSONString(wsResult));
            }
        } catch (IOException e) {
            log.error("Error sending message：{}", e.getMessage());
        }
    }
}
