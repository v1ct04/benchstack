const async = require('async'),
  mongoSeed = require('mongo-seed'),
       path = require('path'),
       monk = require('monk')

const seedPath = path.join(__dirname, "seed/seed-function.js")

function doSeed(dbConfig, doClear, seedTimes, concurrency) {
  let seededCount = 0
  async.series([
      function (next) {
        if (!doClear) {
          return next()
        }
        console.log("Clearing database.")
        mongoSeed.clear(dbConfig.host, dbConfig.port, dbConfig.db, next)
      },
      function (next) {
        console.log(`Starting seed, seed times: ${seedTimes}`)

        async.timesLimit(
            seedTimes,
            concurrency,
            function(n, done) {
              console.log(`Seeding, iteration ${n + 1}.`)
              mongoSeed.load(dbConfig.host, dbConfig.port, dbConfig.db, seedPath, "function",
                  function(err) {
                    if (!err) seededCount++
                    done(err)
                  })
            }, next)
      }
    ], (err) => afterSeed(dbConfig, seededCount, err))
}

function afterSeed(dbConfig, seededCount, err) {
  if (err) {
    console.log(`Failed seed with error: ${err}`)
    console.trace(err)
  } else {
    console.log("Completed seeding database successfully!")
  }

  const db = monk(dbConfig.getDbUrl())
  const metadata = db.get('metadata')

  var scaleFactor = null
  async.waterfall([
      function(next) {
        metadata.findOne({}, next)
      },
      function(obj, next) {
        obj = obj || {scaleFactor: 0}
        scaleFactor = obj.scaleFactor + seededCount
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
  console.log("Creating indexes...")
  async.series([
    next => db.get('pokemon').index({loc: "2dsphere"}, next),
    next => db.get('pokemon').index({loc: "2dsphere", ownerId: 1, stadiumId: 1}, next),
    next => db.get('pokestop').index({loc: "2dsphere"}, next),
    next => db.get('stadium').index({loc: "2dsphere"}, next),
    next => db.get('stadium').index({loc: "2dsphere", ownerId: 1}, next),
    next => db.get('trainer').index({loc: "2dsphere"}, next),
    next => db.get('trainer').index({loc: "2dsphere", team: 1}, next),
    next => db.get('user').index({workerNum: 1}, {unique: true}, next)
  ], done)
}

module.exports = doSeed
