const util = {}
const wordo   = require('wordo'),
      {rlist} = require('randgen');

(function (util) {
  util.genArray = function(size, generator) {
    let arr = Array(size)
    for (let i = 0; i < size; i++) {
      arr[i] = generator()
    }
    return arr
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
}(util))

module.exports = util
