package ai.chat2db.community.jcef.update.v2;

import ai.chat2db.community.jcef.context.JcefContext;
import ai.chat2db.community.jcef.enums.ActionTypeEnum;
import ai.chat2db.community.jcef.enums.UpdatedStatus;
import ai.chat2db.community.jcef.utils.CallJsFunctionUtil;
import ai.chat2db.community.tools.console.ConsoleResult;
import com.alibaba.fastjson2.JSON;

import java.util.Map;

final class JcefUpdateProgressReporter {

    void progress(ConsoleResult consoleResult, long downloaded, long total) {
        int percent = total <= 0 ? 0 : (int) Math.min(99L, downloaded * 100L / total);
        push(consoleResult, percent, UpdatedStatus.Updating);
    }

    void completed(ConsoleResult consoleResult) {
        push(consoleResult, 100, UpdatedStatus.Updated);
    }

    void failed(ConsoleResult consoleResult) {
        push(consoleResult, 0, UpdatedStatus.UpdateFailed);
    }

    private void push(ConsoleResult consoleResult, int percent, UpdatedStatus status) {
        consoleResult.setMessage(Map.of("progress", percent, "status", status.getName()));
        consoleResult.setActionType(ActionTypeEnum.UPDATE_PROGRESS.getName());
        if (JcefContext.getInstance().getBrowser_() != null) {
            CallJsFunctionUtil.callHandleJavaMessage(
                JcefContext.getInstance().getBrowser_(),
                JSON.toJSONString(consoleResult)
            );
        }
    }
}
