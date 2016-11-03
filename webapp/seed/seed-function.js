const pokemon    = require('./pokemon-gen'),
      {genArray} = require('./gen-util')

module.exports = function() {
  return {
    "pokemon": genArray(500, pokemon.randomPokemon)
  }
}
