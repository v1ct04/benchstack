const optionList = [
  {
    name: 'dbHost',
    alias: 'h',
    type: String
  },
  {
    name: 'dbPort',
    alias: 'p',
    type: parseInt
  }
];

function getDbConfig(opts = {}) {
  return {
    user: '',
    passwd: '',
    host: opts.dbHost || process.env.npm_package_config_dbHost || 'localhost',
    port: opts.dbPort || process.env.npm_package_config_dbPort || 27017,
    db:  'pokestack',

    getDbUrl: function() {
      if (this.user && this.password) {
        return `mongodb://${this.user}:${this.passwd}@${this.host}:${this.port}/${this.db}`;
      }
      return `mongodb://${this.host}:${this.port}/${this.db}`;
    }
  }
}
getDbConfig.optionList = optionList

module.exports = getDbConfig
