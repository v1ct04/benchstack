const wordo      = require('wordo'),
      randgen    = require('randgen'),
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
  this.loc = {lng: runif(-180, 180), lat: runif(-90, 90)}

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


function Stadium(defenders = []) {
  PokeStop.call(this)

  this.ownerId = null
  this.defendingPokemons = defenders.map(p => p._id)
  this.influencePoints = rchisq(2) * 10
}

function genStadium(pokemonCollection) {
  let defendingPokemons = util.genArray(
      Math.trunc(rchisq(2) / 2.5),
      () => genPokemon({genMongoId: true}))

  let stadium = new Stadium(defendingPokemons)
  defendingPokemons.forEach(p => p.loc = stadium.loc)
  pokemonCollection.push(...defendingPokemons)
  return stadium
}

module.exports = {
  PokeStop: PokeStop,
  genPokeStop: genPokeStop,
  Stadium: Stadium,
  genStadium: genStadium
}
