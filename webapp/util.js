const util = {};

(function (util) {

  util.autoParamMiddleware = function(tableName, genFunc = null) {
    return function(req, res, next, paramId) {
        req.db.get(tableName).findOne({_id: paramId})
            .then(function(param) {
              if (!param && !genFunc) {
                res.status(404)
                return next(new Error(`Param ${tableName} not found`))
              }
              req[tableName] = param || genFunc()
              next()
            }, next)
      }
  }

  util.offsetDistSq = function (offset) {
    return offset.horz * offset.horz + offset.vert * offset.vert
  }

  const earthRadiusMt = 6378137
  util.offsetLocation = function (loc, offset) {
    let newLat = loc.lat + 180 * offset.vert / (Math.PI * earthRadiusMt)
    let latRad = ((loc.lat + newLat) / 2) * Math.PI / 180
    let newLng = loc.lng + 180 * offset.horz / (Math.PI * earthRadiusMt * Math.cos(latRad))

    newLng %= 360
    newLat %= 180
    if (newLng > 180 || newLng < -180) {
      newLng = newLng - Math.sign(newLng) * 360
    }
    if (newLat > 90 || newLat < -90) {
      newLat = Math.sign(newLat) * 180 - newLat
      newLng = newLng - Math.sign(newLng) * 180
    }
    return {lng: newLng, lat: newLat}
  }
}(util))

module.exports = util
