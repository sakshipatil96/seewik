#!/usr/bin/env python3
"""Generate a preliminary WGS84 GeoJSON traced from the received 2025 map image."""

from __future__ import annotations

import json
from pathlib import Path


OUTPUT = Path(__file__).with_name("official-map-digitized-boundaries-v0.1.geojson")

# Approximate image-to-WGS84 affine registration. The photographed map is north-up.
# The registration uses the railway corridor, the station area, and the major-road
# pattern as visual controls. It is suitable for review, not legal/survey use.
IMAGE_WIDTH = 2910.0
IMAGE_HEIGHT = 4117.0
WEST = 74.2160
EAST = 74.2740
NORTH = 21.4110
SOUTH = 21.3500


P = {
    "o0": (50, 600), "a1": (900, 600), "a2": (1700, 600),
    "o3": (2600, 600), "o4": (2450, 920), "o5": (2650, 930),
    "o6": (2650, 1350), "o7": (2840, 1300), "o8": (2750, 1500),
    "o9": (2750, 1950), "o10": (2650, 2550), "o11": (2600, 2900),
    "o12": (2900, 2950), "o13": (2650, 3050), "o14": (2600, 3400),
    "oe15": (2100, 3400), "oe16": (2600, 3600), "oe17": (2200, 3650),
    "oe18": (1650, 3800), "oe19": (1350, 3800), "oe20": (1300, 4000),
    "oe21": (1000, 3900), "oe22": (900, 3800), "oe23": (650, 3900),
    "oe24": (600, 3600), "o25": (0, 3550), "o26": (0, 3000),
    "o27": (100, 2900), "o28": (0, 2750), "o29": (0, 2500),
    "o30": (0, 1600), "o31": (200, 1800),
    "b12": (950, 1000), "c12": (1030, 1500), "d12": (1100, 1800),
    "b23": (1600, 1000), "c23": (1480, 1400), "q25": (1250, 1600),
    "q16": (850, 1750), "q34": (1900, 1500), "q34b": (2200, 1700),
    "q54": (1800, 1900),
    "r1": (700, 2550), "r2": (1100, 2500), "r3": (1400, 2550),
    "r4": (2000, 2500), "r5": (2650, 2550),
    "s78": (1300, 2900), "s7": (950, 3000), "s14": (650, 3000),
    "s8": (2200, 2950), "s9": (1800, 3000),
    "u1413": (700, 3200), "u14": (650, 3400), "u1312": (950, 3300),
    "v1315": (850, 3400), "p1120": (1120, 3000), "u1211": (1120, 3320),
    "p1300": (1300, 3000), "u1110": (1300, 3350), "u109": (1700, 3400),
    "w17": (650, 3500), "w15": (1050, 3500), "w16": (1300, 3500),
    "w19": (1650, 3500), "x1718": (1050, 3600), "x1819": (1350, 3650),
    "x19": (1650, 3700),
}


# Shared vertices are deliberately reused so adjacent polygons meet exactly.
RINGS = {
    1: ["o0", "a1", "b12", "c12", "d12", "q16", "o31", "o30"],
    2: ["a1", "a2", "b23", "c23", "q25", "d12", "c12", "b12"],
    3: ["a2", "o3", "o4", "o5", "o6", "o7", "o8", "o9", "q34b", "q34", "c23", "b23"],
    4: ["q34", "q34b", "o9", "o10", "r5", "r4", "q54"],
    5: ["d12", "q25", "c23", "q34", "q54", "r4", "r3", "r2"],
    6: ["o30", "o31", "q16", "d12", "r2", "r1", "o29"],
    7: ["o29", "r1", "r2", "r3", "s78", "s7", "s14", "o27", "o28"],
    8: ["r3", "r4", "r5", "o10", "o11", "s8", "s9", "s78"],
    9: ["s9", "s8", "o11", "o12", "o13", "o14", "oe15", "u109"],
    10: ["p1300", "s9", "u109", "u1110"],
    11: ["p1120", "p1300", "u1110", "u1211"],
    12: ["s7", "p1120", "u1211", "u1312"],
    13: ["s14", "s7", "u1312", "v1315", "u14", "u1413"],
    14: ["o27", "s14", "u1413", "u14", "o25", "o26"],
    15: ["u14", "v1315", "u1312", "u1211", "w15", "w17"],
    16: ["u1211", "u1110", "u109", "w19", "w16", "w15"],
    17: ["o25", "u14", "w17", "w15", "x1718", "oe22", "oe23", "oe24"],
    18: ["w15", "w16", "x1819", "oe19", "oe20", "oe21", "oe22", "x1718"],
    19: ["w16", "w19", "x19", "oe18", "oe19", "x1819"],
    20: ["u109", "oe15", "o14", "oe16", "oe17", "oe18", "x19", "w19"],
}


def georef(point: tuple[int, int]) -> list[float]:
    x, y = point
    lon = WEST + (x / IMAGE_WIDTH) * (EAST - WEST)
    lat = NORTH - (y / IMAGE_HEIGHT) * (NORTH - SOUTH)
    return [round(lon, 7), round(lat, 7)]


def signed_area(ring: list[list[float]]) -> float:
    return sum(
        ring[i][0] * ring[i + 1][1] - ring[i + 1][0] * ring[i][1]
        for i in range(len(ring) - 1)
    ) / 2.0


def build_ring(names: list[str]) -> list[list[float]]:
    ring = [georef(P[name]) for name in names]
    ring.append(ring[0])
    if signed_area(ring) < 0:
        ring = [*reversed(ring[:-1])]
        ring.append(ring[0])
    return ring


features = []
for ward_number in range(1, 21):
    prabhag_id = f"PRABHAG-{ward_number:02d}"
    features.append({
        "type": "Feature",
        "id": prabhag_id,
        "properties": {
            "prabhagId": prabhag_id,
            "prabhagName": f"Prabhag {ward_number}",
            "wardNumber": ward_number,
            "geometryType": "POLYGON",
            "resolutionQuality": "APPROXIMATE_DIGITISED_OFFICIAL_MAP_IMAGE",
            "requiresCitizenConfirmation": True,
            "sourceReference": "Nandurbar Municipal Council General Election 2025 FINAL MAP; photographed copy received from Nagar Parishad on 2026-08-24",
            "sourceStatus": "OFFICIAL_SOURCE_IMAGE",
            "reviewStatus": "REVIEW_PENDING_GEOREFERENCE",
            "datasetVersion": "official-map-digitized-v0.1",
            "generatorVersion": "manual-visible-line-trace-v1",
            "generatorSeed": "NOT_APPLICABLE",
            "generatedAt": "2026-08-25T00:00:00Z",
            "isActive": False,
        },
        "geometry": {"type": "Polygon", "coordinates": [build_ring(RINGS[ward_number])]},
    })

collection = {
    "type": "FeatureCollection",
    "name": "Nandurbar prabhag boundaries digitised from official 2025 map image",
    "crs": {"type": "name", "properties": {"name": "urn:ogc:def:crs:OGC:1.3:CRS84"}},
    "metadata": {
        "datasetVersion": "official-map-digitized-v0.1",
        "prabhagCount": 20,
        "sourceDocumentOfficial": True,
        "geometryOfficialDigital": False,
        "digitisationMethod": "manual simplified trace of visible ward lines from photographed/scanned official final map",
        "georeferencingMethod": "approximate affine registration using the railway corridor, Nandurbar station area and major-road pattern",
        "coordinateOrder": "longitude, latitude",
        "accuracy": "unknown; visual-review draft only",
        "fitnessForUse": "geojson.io inspection and correction; not production routing, legal boundary determination or survey use",
        "requiresCitizenConfirmation": True,
    },
    "features": features,
}

OUTPUT.write_text(json.dumps(collection, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print(OUTPUT)
