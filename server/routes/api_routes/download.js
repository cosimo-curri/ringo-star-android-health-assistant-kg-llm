"use strict";

const fs = require("node:fs");
const path = require("node:path");
const router = require("express").Router();

const MODELS = Object.freeze({
  gemma2b: "gemma2b.bin",
  falcon_rw1b: "falcon_rw1b.bin",
  stable_lm3b: "stable_lm3b.bin",
  phi2: "phi2.bin",
});

const modelDirectory = path.resolve(
  process.env.RINGO_MODEL_DIR || path.join(__dirname, "..", "..", "ringo_cloud_storage", "download")
);

for (const [model, filename] of Object.entries(MODELS)) {
  router.get(`/${model}`, (req, res, next) => {
    const file = path.join(modelDirectory, filename);
    fs.stat(file, (error, stat) => {
      if (error) {
        if (error.code === "ENOENT") {
          return res.status(404).json({ error: "Model file not installed on the server" });
        }
        return next(error);
      }
      if (!stat.isFile() || stat.size === 0) {
        return res.status(404).json({ error: "Model file not installed on the server" });
      }
      res.download(file, filename, { headers: { "Cache-Control": "no-store" } }, (sendError) => {
        if (sendError && !res.headersSent) next(sendError);
      });
    });
  });
}

module.exports = router;
