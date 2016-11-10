#!/usr/bin/env node

const async     = require('async'),
      mongoSeed = require('mongo-seed'),
      path      = require('path'),
      parseArgs = require('command-line-args'),
      monk      = require('monk')
const dbConfig  = require('./dbconfig')

const seedPath = path.join(__dirname, "seed/seed-function.js")

const opts = parseArgs([
  {
    name: 'clear',
    alias: 'C',
    type: Boolean,
    defaultValue: false
  },
  {
    name: 'times',
    alias: 'n',
    type: parseInt,
    defaultOption: true,
    defaultValue: 10
  },
  {
    name: 'concurrency',
    alias: 'c',
    type: parseInt,
    defaultValue: require('os').cpus().length * 4
  }
].concat(dbConfig.optionList));

const config = dbConfig(opts)

async.waterfall([
    function (next) {
      if (!opts.clear) {
        return next()
      }
      console.log("Clearing database.")
      mongoSeed.clear(config.host, config.port, config.db, next)
    },
    function (next) {
      console.log(`Starting seed, scale factor: ${opts.times}`)

      let seedCount = 0
      async.timesLimit(
          opts.times,
          opts.concurrency,
          function(n, done) {
            console.log(`Seeding, iteration ${n + 1}.`)
            mongoSeed.load(config.host, config.port, config.db, seedPath, "function",
                function(err) {
                  if (!err) seedCount++
                  done(err)
                })
          },
          (err) => next(err, seedCount))
    }
  ], afterSeed)

function afterSeed(err, seedCount) {
  if (err) {
    console.log(`Failed seed with error: ${err}`)
    console.trace(err)
  } else {
    console.log("Completed seeding database successfully!")
  }

  const db = monk(config.getDbUrl())
  const metadata = db.get('metadata')

  var scaleFactor = null
  async.waterfall([
      function(next) {
        metadata.findOne({}, next)
      },
      function(obj, next) {
        obj = obj || {scaleFactor: 0}
        scaleFactor = obj.scaleFactor + seedCount
        metadata.update({}, {scaleFactor: scaleFactor}, {upsert: true}, next)
      },
      function(result, next) {
        console.log(`Final scale factor: ${scaleFactor}.`)
        db.get('stadium').count({}, next)
      },
      function(stadiumCount, next) {
        if (stadiumCount != 200 * scaleFactor) {
          return next(new Error("Objects count different than expected from SF"))
        }
        createIndexes(db, next)
      }
    ],
    function(err) {
      if (err) console.error(err)
      db.close(true)
      process.exit()
    })
}

function createIndexes(db, done) {
  async.series([
    next => db.get('pokemon').index({loc: "2dsphere", trainerId: 1, stadiumId: 1}, next),
    next => db.get('pokestop').index({loc: "2dsphere"}, next),
    next => db.get('stadium').index({loc: "2dsphere"}, next),
    next => db.get('trainer').index({loc: "2dsphere"}, next)
  ], done)
}
