const mongoose = require('mongoose');
const Route = require('./models/Route');

mongoose.connect('tu_connection_string')
  .then(async () => {
    console.log('Creando índices geoespaciales...');
    await Route.collection.createIndex({ startPoint: "2dsphere" });
    await Route.collection.createIndex({ geometry: "2dsphere" });
    console.log('✅ Índices creados');
    process.exit(0);
  });