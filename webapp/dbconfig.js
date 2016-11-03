module.exports = {
  user: '',
  passwd: '',
  host: process.env.npm_package_config_dbHost || 'localhost',
  port: process.env.npm_package_config_dbPort || 27017,
  db:  'pokestack',

  getDbUrl: function() {
    if (this.user && this.password) {
      return `mongodb://${this.user}:${this.passwd}@${this.host}:${this.port}/${this.db}`;
    }
    return `mongodb://${this.host}:${this.port}/${this.db}`;
  }
};
