const pokemon    = require('./pokemon-gen'),
      {genArray} = require('./gen-util')

module.exports = function() {
  return {
    "pokemon": genArray(10000, pokemon.randomPokemon)
  }
}
