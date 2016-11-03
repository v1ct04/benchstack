const {genArray} = require('./gen-util'),
    {genPokemon} = require('./pokemon-gen'),
    {genPokeStop, genStadium} = require('./pokestops-gen')

module.exports = function() {
  return (function(data) {
    data.pokemon = genArray(10000, genPokemon)
    data.pokestop = genArray(1500, genPokeStop)
    data.stadium = genArray(200, () => genStadium(data.pokemon))
    return data
  })({})
}
