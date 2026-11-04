#!/usr/bin/env python3
"""Create a reproducible small offline OSM road-segment index for airport-driving routes.

Input: Geofabrik Uruguay PBF (ODbL); output: ZIP of small ~1.1km JSON grid tiles.
Includes tagged and untagged driveable ways: never attach an adjacent road's
speed limit to the user just because that other road was mapped better.
"""
import collections
import datetime
import json
import math
import pathlib
import sys
import zipfile

import osmium

LAT_MIN, LAT_MAX = -35.15, -34.05
LON_MIN, LON_MAX = -57.3, -55.2
TILE = 100
MARGIN = 0.00048  # ~44-53 meters around segment for boundary-safe tile lookup
DRIVABLE = {
    'motorway', 'motorway_link', 'trunk', 'trunk_link',
    'primary', 'primary_link', 'secondary', 'secondary_link',
    'tertiary', 'tertiary_link', 'unclassified', 'residential',
    'service', 'living_street', 'road',
}

def speed(value):
    if not value:
        return 0
    v = value.strip().lower()
    if v.endswith(' km/h'):
        v = v[:-5].strip()
    if v.endswith(' mph'):
        try:
            n = round(float(v[:-4].strip()) * 1.609344)
            return n if 5 <= n <= 160 else 0
        except ValueError:
            return 0
    if not v.isdigit():
        return 0
    n = int(v)
    return n if 5 <= n <= 150 else 0


class Reader(osmium.SimpleHandler):
    def __init__(self):
        super().__init__()
        self.tiles = collections.defaultdict(list)
        self.ways_seen = 0
        self.segments = 0
        self.tagged_segments = 0

    def way(self, way):
        tags=way.tags
        typ = tags.get('highway', '')
        if typ not in DRIVABLE:
            return
        try:
            nodes=[(round(n.lat,5),round(n.lon,5)) for n in way.nodes if n.location.valid()]
        except Exception:
            return
        if len(nodes)<2:
            return
        n=speed(tags.get('maxspeed',''))
        if not n:
            forward=speed(tags.get('maxspeed:forward',''))
            backward=speed(tags.get('maxspeed:backward',''))
            if forward>0 and forward==backward:
                n=forward
        road_id=int(way.id)
        name=tags.get('name','')[:70]
        self.ways_seen+=1
        for (lat1,lon1),(lat2,lon2) in zip(nodes,nodes[1:]):
            if lat1 == lat2 and lon1 == lon2:
                continue
            lo_lat=min(lat1,lat2)
            hi_lat=max(lat1,lat2)
            lo_lon=min(lon1,lon2)
            hi_lon=max(lon1,lon2)
            if hi_lat < LAT_MIN or lo_lat > LAT_MAX or hi_lon < LON_MIN or lo_lon > LON_MAX:
                continue
            lat_low=max(LAT_MIN,lo_lat-MARGIN)
            lat_high=min(LAT_MAX,hi_lat+MARGIN)
            lon_low=max(LON_MIN,lo_lon-MARGIN)
            lon_high=min(LON_MAX,hi_lon+MARGIN)
            key=[road_id,name,n,lat1,lon1,lat2,lon2]
            self.segments+=1
            if n:
                self.tagged_segments+=1
            for ilat in range(math.floor(lat_low*TILE),math.floor(lat_high*TILE)+1):
                for ilon in range(math.floor(lon_low*TILE),math.floor(lon_high*TILE)+1):
                    self.tiles[(ilat,ilon)].append(key)


def main():
    source=pathlib.Path(sys.argv[1])
    output=pathlib.Path(sys.argv[2])
    output.parent.mkdir(parents=True,exist_ok=True)
    reader=Reader()
    reader.apply_file(str(source),locations=True,idx='flex_mem')
    if reader.ways_seen < 1000 or reader.tagged_segments < 100:
        raise RuntimeError('Too few mapped roads to publish an offline dataset')
    with zipfile.ZipFile(output,'w',compression=zipfile.ZIP_DEFLATED,compresslevel=7,allowZip64=True) as z:
        for (ilat,ilon),segments in reader.tiles.items():
            name=f'{ilat}_{ilon}.json'
            z.writestr(name,json.dumps(segments,separators=(',',':'),ensure_ascii=False))
        z.writestr('_source.json',json.dumps({
            'license':'Open Database License (ODbL) 1.0',
            'attribution':'© OpenStreetMap contributors · Geofabrik GmbH',
            'url':'https://download.geofabrik.de/south-america/uruguay.html',
            'source_file':source.name,
            'coverage':{'lat':[LAT_MIN,LAT_MAX],'lon':[LON_MIN,LON_MAX]},
            'tile_degrees':1/TILE,
            'generated_utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),
            'tagged_segments':reader.tagged_segments,
            'segments':reader.segments
        },ensure_ascii=False))
    mb=output.stat().st_size/1048576
    print('OFFLINE_DB_SUCCESS',flush=True)
    print('OFFLINE_DB_TILES',len(reader.tiles),'WAYS',reader.ways_seen,
          'SEGMENTS',reader.segments,'TAGGED_SEGMENTS',reader.tagged_segments,
          'PERCENT_TAGGED',round(reader.tagged_segments/max(1,reader.segments)*100,1),
          'ZIP_MIB',round(mb,2),flush=True)
    if mb>35:
        raise RuntimeError('Offline database unexpectedly large for this app')


if __name__=='__main__':
    main()
