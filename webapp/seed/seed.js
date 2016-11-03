const async     = require('async'),
      mongoSeed = require('mongo-seed'),
      path      = require('path')
const {host, port, db}  = require('../dbconfig')

const scaleFactor = parseInt(process.argv[2]) || 10
const seedPath = path.join(__dirname, "seed-function.js")

async.waterfall([
    function (next) {
      mongoSeed.clear(host, port, db, next)
    },
    function (done) {
      console.log(`Starting seed, scale factor: ${scaleFactor}`)

      async.timesSeries(scaleFactor, function(n, next) {
        console.log(`Seeding database, iteration ${n + 1}`)
        mongoSeed.load(host, port, db, seedPath, "function", next)
      }, done)
    }
  ],
  function (err, results) {
    if (err) throw err
    
    console.log("Finished seeding database!")
    process.exit()
  });