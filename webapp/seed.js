#!/usr/bin/env node

const async     = require('async'),
      mongoSeed = require('mongo-seed'),
      path      = require('path'),
      parseArgs = require('command-line-args')
const {host, port, db}  = require('./dbconfig')

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
]);

async.waterfall([
    function (next) {
      if (!opts.clear) {
        return next()
      }
      console.log("Clearing database.")
      mongoSeed.clear(host, port, db, next)
    },
    function (done) {
      console.log(`Starting seed, scale factor: ${opts.times}`)

      async.timesLimit(
          opts.times,
          opts.concurrency,
          function(n, next) {
            console.log(`Seeding, iteration ${n + 1}.`)
            mongoSeed.load(host, port, db, seedPath, "function", next)
          },
          (err) => done(err))
    }
  ],
  function (err) {
    if (err) throw err
    
    console.log("Finished seeding database!")
    process.exit()
  });