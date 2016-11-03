exports.genArray = function (size, generator) {
  let arr = []
  for (let i = 0; i < size; i++) {
    arr.push(generator())
  }
  return arr
}
