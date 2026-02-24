UPDATE short_circuit_specific_parameters
SET value_ = REPLACE(value_, '"type":"WIND"', '"type":"GENERATOR"')
where name = 'powerElectronicsClusters';
UPDATE short_circuit_specific_parameters
SET value_ = REPLACE(value_, '"type":"SOLAR"', '"type":"GENERATOR"')
where name = 'powerElectronicsClusters';