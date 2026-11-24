"use strict";

const assert = require("node:assert/strict");
const { mkdtempSync, writeFileSync, rmSync } = require("node:fs");
const { tmpdir } = require("node:os");
const path = require("node:path");
const http = require("node:http");
const test = require("node:test");

const modelDirectory = mkdtempSync(path.join(tmpdir(), "ringo-model-test-"));
process.env.RINGO_MODEL_DIR = modelDirectory;
const app = require("../server");

const models = {
  gemma2b: "gemma2b.bin",
  falcon_rw1b: "falcon_rw1b.bin",
  stable_lm3b: "stable_lm3b.bin",
  phi2: "phi2.bin",
};

async function request(server, endpoint) {
  const { port } = server.address();
  return new Promise((resolve, reject) => {
    http.get({ hostname: "127.0.0.1", port, path: endpoint }, (res) => {
      const chunks = [];
      res.on("data", (part) => chunks.push(part));
      res.on("end", () => resolve({ status: res.statusCode, headers: res.headers, body: Buffer.concat(chunks) }));
      res.on("error", reject);
    }).on("error", reject);
  });
}

test("download API keeps the four original endpoints and file names", async (t) => {
  const server = app.listen(0, "127.0.0.1");
  await new Promise((resolve, reject) => {
    server.once("listening", resolve);
    server.once("error", reject);
  });
  t.after(() => server.close());
  for (const [model, filename] of Object.entries(models)) {
    await t.test(model, async () => {
      const bytes = Buffer.from(`fixture-for-${model}`);
      writeFileSync(path.join(modelDirectory, filename), bytes);
      const result = await request(server, `/api/download/${model}`);
      assert.equal(result.status, 200);
      assert.deepEqual(result.body, bytes);
      assert.ok(result.headers["content-disposition"].includes(filename));
    });
  }
});

test("missing and unknown models do not disclose local file paths", async (t) => {
  const server = app.listen(0, "127.0.0.1");
  await new Promise((resolve, reject) => {
    server.once("listening", resolve);
    server.once("error", reject);
  });
  t.after(() => server.close());
  const { unlinkSync } = require("node:fs");
  unlinkSync(path.join(modelDirectory, "phi2.bin"));
  const missing = await request(server, "/api/download/phi2");
  assert.equal(missing.status, 404);
  assert.doesNotMatch(missing.body.toString(), /ringo-model-test|ENOENT/);
  const unknown = await request(server, "/api/download/not-a-model");
  assert.equal(unknown.status, 404);
});

test("empty model files are not offered for download", async (t) => {
  const server = app.listen(0, "127.0.0.1");
  await new Promise((resolve) => server.once("listening", resolve));
  t.after(() => server.close());
  writeFileSync(path.join(modelDirectory, "gemma2b.bin"), Buffer.alloc(0));
  const result = await request(server, "/api/download/gemma2b");
  assert.equal(result.status, 404);
});

test.after(() => rmSync(modelDirectory, { recursive: true, force: true }));
