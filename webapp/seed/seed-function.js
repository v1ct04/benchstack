const {genArray} = require('./gen-util'),
    {genPokemon} = require('./gen-pokemon'),
    {genPokeStop, genStadium} = require('./gen-pokestop')

module.exports = function() {
  return (function(data) {
    data.pokemon = genArray(10000, genPokemon)
    data.pokestop = genArray(1500, genPokeStop)
    data.stadium = genArray(200, () => genStadium(data.pokemon))
    return data
  })({})
}
