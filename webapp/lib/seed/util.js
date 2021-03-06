const util = {}
const wordo   = require('wordo'),
      nombres = require('nombres'),
      {rlist, runif} = require('randgen'),
      offsetLocation = require('../util').offsetLocation;

(function (util) {

  util.genArray = function(size, generator) {
    let arr = Array(size)
    for (let i = 0; i < size; i++) {
      arr[i] = generator()
    }
    return arr
  }

  util.limit = function (v, {min = -Infinity, max = Infinity}) {
    if (v < min) v = min
    if (v > max) v = max
    return Math.trunc(v)
  }

  util.countIf = function (arr, predicate) {
    let count = 0
    for (let i = 0; i < arr.length; i++) {
      if (predicate(arr[i], i, arr)) count++
    }
    return count
  }

  util.mapToObj = function(collection, func) {
    let obj = {}
    collection.forEach(elm => obj[elm] = func(elm))
    return obj
  }

  function capitalizeFirst(str) {
    let c = str[0]
    return str.replace(c, c.toUpperCase())
  }

  util.radjctv = function(type = 'all') {
    return capitalizeFirst(rlist(wordo.adjectives[type]))
  }

  util.rnoun = function(type = 'all') {
    return capitalizeFirst(rlist(wordo.nouns[type]))
  }

  util.rname = function(forenames = 1, surnames = 1) {
    let fores = util.genArray(forenames, () => rlist(nombres.fore))
    let surs = util.genArray(surnames, () => rlist(nombres.sur))
    return fores.concat(surs).join(' ')
  }

  util.rloc = function(center = null, maxRadius = 10000) {
    if (!center) {
      return {lng: runif(-180, 180), lat: runif(-90, 90)}
    }
    let radius = runif(0, maxRadius), angle = runif(-Math.PI, Math.PI)
    let offset = {horz: radius * Math.cos(angle), vert: radius * Math.sin(angle)}
    return offsetLocation(center, offset)
  }
}(util))

module.exports = util
