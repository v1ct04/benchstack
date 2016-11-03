exports.genArray = function (size, generator) {
  let arr = Array(size)
  for (let i = 0; i < size; i++) {
    arr[i] = generator()
  }
  return arr
}
