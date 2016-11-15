const pokemon   = require('pokemon'),
      baseStats = require('pokemon-base-stats'),
      statsCalc = require('pokemon-stat-calculator'),
      MongoID   = require('mongodb').ObjectID
      wordo     = require('wordo'),
      {rchisq, runif, rlist} = require('randgen')
const natures = require('./pokemon-natures'),
      util    = require('./util')

function randomIVs() {
  // check http://bulbapedia.bulbagarden.net/wiki/Individual_values#Generation_I_and_II
  let baseIVs = util.genArray(4, () => util.limit(rchisq(3) * 2, {max: 15}))
  let hpIV = baseIVs
    .map((v, i) => (v & 1) << i)
    .reduce((a, b) => a | b)
  return [hpIV, baseIVs[0], baseIVs[1], baseIVs[3], baseIVs[3], baseIVs[2]]
}

function randomEVs(level) {
  // check http://bulbapedia.bulbagarden.net/wiki/Effort_values#Stat_experience
  let mult = rchisq() * Math.pow(level, 1.4)
  let EVs = util.genArray(
      6,
      () => util.limit(Math.sqrt(rchisq(3) * 40 * mult), {max: 255}))

  let evSum = () => EVs.reduce((a, b) => a + b)
  while (evSum() > 510) {
    diff = (evSum() - 510) / util.countIf(EVs, v => v > 0)
    EVs = EVs.map(v => util.limit(v - diff, {min: 0}));
  }
  return EVs;
}

function genPokemonName(id) {
  const adjTypes = [
    ['appearance', 'feelings'],
    ['feelings', 'colour'],
    ['size', 'taste'],
    ['sound', 'condition'],
    ['numbers', 'shape']
  ]
  let name = rlist(adjTypes).map(util.radjctv)
  name.push(pokemon.getName(id))
  return name.join(' ')
}

// Exported API

function Pokemon(pkmid, form, nature, level, IVs, EVs, extraArgs = {}) {
  let {genMongoId, loc, ownerId, stadiumId} = extraArgs
  if (genMongoId) this._id = new MongoID()
  this.pkmid = pkmid
  this.name = genPokemonName(pkmid)
  this.originalNames = util.mapToObj(pokemon.languages, l => pokemon.getName(pkmid, l))
  this.form = form
  this.nature = nature
  this.level = level

  let base = baseStats.getById({id: pkmid, forme: form})
  let natureVals = natures.values[nature]
  let stats = statsCalc.calAllStats(IVs, base, EVs, level, natureVals)
  this.stats = {
    HP: stats[0],
    Atk: stats[1],
    Def: stats[2],
    SpAtk: stats[3],
    SpDef: stats[4],
    Spd: stats[5]
  }
  this.loc = loc || util.rloc()
  this.ownerId = ownerId || null
  this.stadiumId = stadiumId || null
}

function genPokemon(args = {}) { // args = {id, name, level, loc, ownerId, stadiumId, genMongoId}
  let {id, name, level} = args
  if (!id) {
    if (name) {
      id = pokemon.getId(name)
    } else {
      id = runif(0, pokemon.all().length, true) + 1
    }
  }
  if (!level) {
    level = util.limit(rchisq(2) * 5, {min: 1, max: 100})
  }
  let form = rlist(baseStats.getFormes({id: id})),
      nature = rlist(natures.names)
  return new Pokemon(id, form, nature, level, randomIVs(), randomEVs(level), args)
}

exports.Pokemon = Pokemon
exports.genPokemon = genPokemon
