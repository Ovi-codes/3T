-- Increment 9: the weather forecast.
--
-- A forecast is location-driven, so a location needs coordinates to ask the weather API for
-- the right point (charter §3 seam: forecast is data about a place, not about an event).
-- Columns are nullable so a location without coordinates simply has no forecast — the seam
-- degrades rather than forcing every future location to carry coordinates.
--

alter table location
    add column latitude  numeric(8, 5),
    add column longitude numeric(8, 5);

-- Backfill the one V1 location. Tineretului Park, Bucharest.
update location
set latitude  = 44.40850,
    longitude = 26.10390
where city = 'Bucharest'
  and name = 'Tineretului Park';
