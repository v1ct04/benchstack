const wordo      = require('wordo'),
      randgen    = require('randgen'),
      nombres    = require('nombres'),
      MongoID    = require('mongodb').ObjectID,
    {genPokemon} = require('./gen-pokemon')
const natures = require('./pokemon-natures'),
      util    = require('./util')

let {rchisq, runif} = randgen

function genTrainerName() {
  let fores = 1 + Math.trunc(rchisq() / 3)
  let surs = 1 + Math.trunc(rchisq() / 2)
  return util.rname(fores, surs)
}

function nicknameFromName(name) {
  let names = name.toLowerCase().split(' ')
  return names[0] +
         names.slice(1, names.length - 1).map(n => n[0]) +
         names[names.length - 1]
}

const Teams = ['Red', 'Yellow', 'Blue']

// Exported API

function Trainer() {
  this._id = new MongoID()
  this.name = genTrainerName()
  this.nickname = nicknameFromName(this.name)
  this.age = Math.trunc(15 + rchisq() * 5)
  this.joinedOn = new Date(Date.now() - 604800000 * rchisq())

  this.loc = util.rloc()
  this.team = randgen.rlist(Teams)

  this.bag = {
    pokeball: Math.trunc(rchisq(3) * 5),
    greatball: Math.trunc(rchisq() * 2),
    revive: Math.trunc(rchisq() * 3),
    lure: Math.trunc(rchisq())
  }
  this.pokemonIds = []
}

function genTrainer(pokemonCollection) {
  let trainer = new Trainer()

  let args = {genMongoId: true, ownerId: trainer._id, loc: trainer.loc}
  let pokemons = util.genArray(1 + Math.trunc(rchisq()), () => genPokemon(args))
  trainer.pokemonIds.push(...pokemons.map(p => p._id))
  pokemonCollection.push(...pokemons)

  return trainer
}

module.exports = {
  Trainer: Trainer,
  genTrainer: genTrainer
}
