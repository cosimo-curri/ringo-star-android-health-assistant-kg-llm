"use strict";

const express = require("express");
const apiRouter = require("./routes/api");

const app = express();
app.disable("x-powered-by");
app.use("/api", apiRouter);

// Do not expose filesystem paths or implementation details to HTTP clients.
app.use((error, req, res, next) => {
  if (res.headersSent) return next(error);
  console.error("Model server request failed:", error.message);
  res.status(500).json({ error: "Unable to serve the requested model" });
});

if (require.main === module) {
  const port = Number(process.env.PORT || 3000);
  const host = process.env.HOST || "127.0.0.1";
  if (!Number.isInteger(port) || port < 1 || port > 65535) {
    throw new Error("PORT must be an integer between 1 and 65535");
  }
  app.listen(port, host, () => console.log(`Ringo-Star model server on ${host}:${port}`));
}

module.exports = app;
