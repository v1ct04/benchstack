const {genArray} = require('./util'),
    {genPokemon} = require('./gen-pokemon'),
    {genTrainer} = require('./gen-trainer'),
    {genPokeStop, genStadium} = require('./gen-pokestop')

module.exports = function() {
  return (function(data) {
    data.pokemon = genArray(10000, genPokemon)
    data.pokestop = genArray(1500, genPokeStop)
    data.stadium = genArray(200, () => genStadium(data.pokemon))
    data.trainer = genArray(50, () => genTrainer(data.pokemon))
    return data
  })({})
}
