const os = require('os');
const { readVersion } = require('./utils');
const Analytics4 = require('./ga4');

function registerAnalytics() {
  const analytics = new Analytics4('G-V8M4E5SF61', 'LShbzC_vRka5Sw5AWco7Tw');
  const customParams = {
    platform: 'DESKTOP',
    version: readVersion(),
    os: os.platform(),
  };
  analytics.setParams(customParams).event('first_enter');
}

module.exports = registerAnalytics;
