import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { EventEmitter } from "node:events";
import vm from "node:vm";
import { executeShell, presentNative, checkedMutationPath, cleanupOutputSpools } from "../../main/resources/agent/chat2db-output.mjs";

const registered = new Map();
const listeners = new Map();
const calls = [];
const uploaded = [];
const sourceBytes = Buffer.from("first-record\n" + "详细结果\n".repeat(20000) + "exit-diagnostic\n");
let commands = 0;
const imports = {
  "node:fs": { readFileSync: () => JSON.stringify({ baseUrl: "http://127.0.0.1", ticket: "fixture", tools: [] }), realpathSync: value => value },
  "node:path": { join: (...parts) => parts.join("/") },
  "node:http": { request(url, options, respond) {
    const request = new EventEmitter();
    request.destroy = () => {};
    request.end = body => queueMicrotask(() => {
      assert.equal(options.headers.Authorization, "Bearer fixture");
      const args = JSON.parse(body);
      calls.push({ url, ...args });
      let data;
      if (url.endsWith("/prepare-native")) data = { workingDirectory: "/fixture", preparationId: "prepared" };
      else if (url.endsWith("/execute")) data = { success: true, data: { ok: true, data: { content: "page", nextCursor: "next", hasMore: true } } };
      else {
        assert.equal(args.arguments.preparationId, "prepared");
        const action = args.arguments.action;
        if (action === "begin") data = { uploadId: "upload" };
        else if (action === "append") { uploaded.push(Buffer.from(args.arguments.content, "base64")); data = { accepted: true }; }
        else if (action === "finish") data = { mode: "file", artifactId: "artifact", path: "/managed/output.txt", complete: args.arguments.complete };
        else if (action === "present") data = args.arguments.result;
        else throw new Error(action);
        data = { success: true, data };
      }
      const response = new EventEmitter();
      response.statusCode = 200;
      response.setEncoding = () => {};
      respond(response);
      response.emit("data", JSON.stringify(data));
      response.emit("end");
    });
    return request;
  } },
  "./chat2db-output.mjs": { executeShell, presentNative, checkedMutationPath, cleanupOutputSpools },
  "@earendil-works/pi-coding-agent": {
    ...Object.fromEntries(["Read", "Edit", "Write", "Grep", "Find", "Ls", "Bash", "PowerShell"].map(name => ["create" + name + "Tool", () => ({ name: name.toLowerCase(), parameters: { type: "object", properties: {} }, execute() { throw new Error("Read must use bounded server access"); } })])),
    createLocalBashOperations: () => ({ async exec(_command, _cwd, { onData }) { commands++; onData(sourceBytes); return { exitCode: 7 }; } }),
    createLocalPowerShellOperations: () => { throw new Error("Wrong shell"); },
  },
};
const context = vm.createContext({ process: { env: { PI_CODING_AGENT_DIR: "/fixture" }, cwd: () => "/fixture", platform: "linux" }, AbortController, AbortSignal });
const module = new vm.SourceTextModule(readFileSync(new URL("../../main/resources/agent/chat2db-tools.mjs", import.meta.url), "utf8"), { context });
await module.link(specifier => {
  const values = imports[specifier]; assert.ok(values, specifier);
  return new vm.SyntheticModule(Object.keys(values), function () {
    for (const [key, value] of Object.entries(values)) this.setExport(key, value);
  }, { context });
});
await module.evaluate();
module.namespace.default({ registerCommand() {}, registerTool: tool => registered.set(tool.name, tool),
  on: (name, handler) => listeners.set(name, handler) });

const read = await registered.get("read").execute("read-call", { path: "/managed/output.txt", cursor: "cursor", description: "Read more rows" });
assert.equal(read.details.data.content, "page");
assert.equal(calls.length, 1);
assert.ok(calls[0].url.endsWith("/execute"));
assert.equal(calls[0].arguments.description, undefined);
assert.equal(calls[0].arguments.cursor, "cursor");
assert.ok(registered.get("read").parameters.properties.cursor);
for (const name of ["ls", "find"]) {
  await registered.get(name).execute(name + "-call", { path: ".", pattern: "*.txt", limit: 1500, description: "List files" });
  assert.ok(calls.at(-1).url.endsWith("/execute"));
  assert.equal(calls.at(-1).arguments.limit, 1500);
}
const shell = await registered.get("bash").execute("shell-call", { command: "fixture", description: "Inspect output" });
assert.equal(shell.details.ok, false);
assert.equal(shell.details.data.exitCode, 7);
assert.equal(shell.details.output.path, "/managed/output.txt");
assert.equal(shell.details.output.complete, true);
assert.deepEqual(Buffer.concat(uploaded), sourceBytes);
assert.ok(calls.filter(call => call.arguments.action === "append").every(call => call.arguments.content.length <= 65536));
assert.equal(listeners.get("tool_result")({ toolName: "bash", details: shell.details }).isError, true);
const replay = await registered.get("bash").execute("shell-call", { command: "fixture", description: "Replay" });
assert.equal(replay, shell);
assert.equal(commands, 1);
console.log("Managed read routing, description stripping, chunk upload, failed exit and execution replay passed");
