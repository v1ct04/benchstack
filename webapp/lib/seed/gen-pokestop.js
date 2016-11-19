const randgen    = require('randgen'),
    MongoID      = require('mongodb').ObjectID,
    {genPokemon} = require('./gen-pokemon')
const natures = require('./pokemon-natures'),
      util    = require('./util')

function genPokeStopName() {
  const adjTypes = [
    ['size', 'shape', 'colour'],
    ['quantity', 'appearance'],
    ['sound', 'touch'],
    ['quantity', 'shape'],
    ['time', 'sound']
  ]
  let name = randgen.rlist(adjTypes).map(util.radjctv)
  name.push(util.rnoun('other'))
  return name.join(' ')
}

// Exported API

let {rchisq, runif} = randgen

function PokeStop() {
  this.name = genPokeStopName()
  this.heightMts = rchisq(3) * 5
  this.radiusMts = rchisq() * 4
  this.loc = util.rloc()

  this.items = {
    pokeball: Math.trunc(rchisq() * 3),
    lure: randgen.rbernoulli(0.2) + randgen.rbernoulli(0.05),
    greatball: Math.trunc(rchisq() / 3),
    revive: Math.trunc(rchisq(2))
  }
}

function genPokeStop() {
  return new PokeStop()
}


function Stadium() {
  PokeStop.call(this)

  this._id = new MongoID()
  this.ownerId = null
  this.defendingPokemonIds = []
  this.points = Math.trunc(rchisq(2) * 10)
}

function genStadium(pokemonCollection) {
  let stadium = new Stadium()

  let pokemonCount = Math.trunc(rchisq(2) / 2.5)
  if (pokemonCount > 0) {
    let args = {genMongoId: true, stadiumId: stadium._id, loc: stadium.loc}
    let pokemons = util.genArray(pokemonCount, () => genPokemon(args))
    stadium.defendingPokemonIds.push(...pokemons.map(p => p._id))
    pokemonCollection.push(...pokemons)
  }
  return stadium
}

module.exports = {
  PokeStop: PokeStop,
  genPokeStop: genPokeStop,
  Stadium: Stadium,
  genStadium: genStadium
}
