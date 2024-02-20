const path = require('path');

module.exports = {
  entry: './index.js',
  output: {
    filename: 'main.js',
    path: path.resolve(__dirname),
  },
  target: 'electron-main',
};
