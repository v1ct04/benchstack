const express = require('express'),
         path = require('path'),
   bodyParser = require('body-parser'),
         monk = require('monk'),
       logger = require('morgan'),
           fs = require('fs')

const routesFolder = path.join(__dirname, "routes")

function createApp(dbConfig, port, logFormat) {
  const db = monk(dbConfig.getDbUrl())

  var app = express()
  app.set('port', port)

  if (logFormat) {
    app.use(logger(logFormat))
  }
  app.use(bodyParser.json())
  app.use(bodyParser.urlencoded({extended: true}))

  // Make db accessible to router middleware
  app.use(function(req, res, next){
    req.db = db;
    next();
  });

  fs.readdirSync(routesFolder)
      .filter(f => path.extname(f) == '.js')
      .map(f => './' + path.join('routes', f))
      .forEach(f => app.use('/api/' + path.basename(f, '.js'), require(f)))

  // common finalization functions

  app.use(function(req, res, next) {
    if (!req.accepts("json") || !res.data) return next()

    res.json({success: 1, data: res.data})
  })

  app.use(function(err, req, res, next) {
    if (!res.statusCode || res.statusCode == 200) {
      res.status(500)
    }
    if (!req.accepts("json")) return next()

    console.error(err)
    res.json({success: 0, err: err.toString()})
  })
  return app;
}

module.exports = createApp;
