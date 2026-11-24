"use strict";

const router = require("express").Router();
router.use("/download", require("./api_routes/download"));
module.exports = router;
