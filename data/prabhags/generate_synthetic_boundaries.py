#!/usr/bin/env python3
"""Generate the deterministic Seewik synthetic prabhag boundary dataset.

The output is development/demo data. It is not an official municipal boundary
or an inferred statement about the real shape of any Nandurbar prabhag.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import sys
from pathlib import Path
from typing import Iterable


DATASET_VERSION = "synthetic-v0.1"
GENERATOR_VERSION = "seeded-voronoi-v1"
GENERATOR_SEED = "seewik-nandurbar-synthetic-boundaries-v0.1"
GENERATED_AT = "2026-08-23T00:00:00Z"

# Nominatim's search extent for OpenStreetMap city node 245694497. This is a
# map-search extent around the named city point, not a municipal boundary.
MIN_LAT = 21.2037780
MAX_LAT = 21.5237780
MIN_LNG = 74.0811418
MAX_LNG = 74.4011418
EXTENT_SOURCE = (
    "https://nominatim.openstreetmap.org/search?"
    "q=Nandurbar%2C%20Maharashtra%2C%20India&format=jsonv2&addressdetails=1&limit=5"
)
OSM_CITY_NODE = "https://www.openstreetmap.org/node/245694497"

ROWS = 5
COLUMNS = 4
PRABHAG_COUNT = ROWS * COLUMNS
ROUND_DIGITS = 7

SCRIPT_DIR = Path(__file__).resolve().parent
GEOJSON_PATH = SCRIPT_DIR / "synthetic-boundaries-v0.1.geojson"
NDJSON_PATH = SCRIPT_DIR / "synthetic-boundaries-v0.1.ndjson"
CHECKSUM_PATH = SCRIPT_DIR / "synthetic-boundaries-v0.1.sha256"

Point = tuple[float, float]


def stable_unit_interval(label: str) -> float:
    digest = hashlib.sha256(f"{GENERATOR_SEED}:{label}".encode("utf-8")).digest()
    return int.from_bytes(digest[:8], "big") / ((1 << 64) - 1)


def generate_sites() -> list[Point]:
    """Return one stable, lightly jittered site inside each 4x5 grid cell."""
    cell_width = (MAX_LNG - MIN_LNG) / COLUMNS
    cell_height = (MAX_LAT - MIN_LAT) / ROWS
    sites: list[Point] = []
    for row in range(ROWS):
        for column in range(COLUMNS):
            jitter_x = (stable_unit_interval(f"{row}:{column}:x") - 0.5) * 0.5
            jitter_y = (stable_unit_interval(f"{row}:{column}:y") - 0.5) * 0.5
            lng = MIN_LNG + (column + 0.5 + jitter_x) * cell_width
            lat = MIN_LAT + (row + 0.5 + jitter_y) * cell_height
            sites.append((lng, lat))
    return sites


def clip_to_half_plane(polygon: list[Point], a: float, b: float, c: float) -> list[Point]:
    """Clip a convex polygon to a*x + b*y <= c."""
    if not polygon:
        return []

    def value(point: Point) -> float:
        return a * point[0] + b * point[1] - c

    output: list[Point] = []
    previous = polygon[-1]
    previous_value = value(previous)
    previous_inside = previous_value <= 1e-12

    for current in polygon:
        current_value = value(current)
        current_inside = current_value <= 1e-12
        if current_inside != previous_inside:
            denominator = previous_value - current_value
            if abs(denominator) > 1e-15:
                t = previous_value / denominator
                output.append(
                    (
                        previous[0] + t * (current[0] - previous[0]),
                        previous[1] + t * (current[1] - previous[1]),
                    )
                )
        if current_inside:
            output.append(current)
        previous = current
        previous_value = current_value
        previous_inside = current_inside
    return output


def voronoi_cell(site_index: int, sites: list[Point]) -> list[Point]:
    polygon: list[Point] = [
        (MIN_LNG, MIN_LAT),
        (MAX_LNG, MIN_LAT),
        (MAX_LNG, MAX_LAT),
        (MIN_LNG, MAX_LAT),
    ]
    site_x, site_y = sites[site_index]
    for other_index, (other_x, other_y) in enumerate(sites):
        if other_index == site_index:
            continue
        a = 2.0 * (other_x - site_x)
        b = 2.0 * (other_y - site_y)
        c = other_x * other_x + other_y * other_y - site_x * site_x - site_y * site_y
        polygon = clip_to_half_plane(polygon, a, b, c)
        if not polygon:
            raise RuntimeError(f"Voronoi cell {site_index + 1} became empty")
    return deduplicate([(round(x, ROUND_DIGITS), round(y, ROUND_DIGITS)) for x, y in polygon])


def deduplicate(points: Iterable[Point]) -> list[Point]:
    result: list[Point] = []
    for point in points:
        if not result or point != result[-1]:
            result.append(point)
    if len(result) > 1 and result[0] == result[-1]:
        result.pop()
    return result


def polygon_area(points: list[Point]) -> float:
    return abs(
        sum(
            points[index][0] * points[(index + 1) % len(points)][1]
            - points[(index + 1) % len(points)][0] * points[index][1]
            for index in range(len(points))
        )
        / 2.0
    )


def closed_coordinates(points: list[Point]) -> list[list[float]]:
    return [[x, y] for x, y in [*points, points[0]]]


def polygon_wkt(points: list[Point]) -> str:
    closed = [*points, points[0]]
    coordinates = ", ".join(f"{x:.7f} {y:.7f}" for x, y in closed)
    return f"POLYGON(({coordinates}))"


def build_outputs() -> tuple[str, str, str]:
    sites = generate_sites()
    features: list[dict[str, object]] = []
    rows: list[dict[str, object]] = []
    total_area = 0.0

    for index, site in enumerate(sites, start=1):
        polygon = voronoi_cell(index - 1, sites)
        area = polygon_area(polygon)
        if len(polygon) < 3 or area <= 0:
            raise RuntimeError(f"Invalid polygon generated for PRABHAG-{index:02d}")
        total_area += area
        prabhag_id = f"PRABHAG-{index:02d}"
        properties = {
            "prabhagId": prabhag_id,
            "prabhagName": f"Prabhag {index}",
            "geometryType": "POLYGON",
            "resolutionQuality": "SYNTHETIC_BOUNDARY",
            "requiresCitizenConfirmation": True,
            "sourceReference": EXTENT_SOURCE,
            "sourceStatus": "UNSOURCED",
            "reviewStatus": "REVIEW_PENDING",
            "datasetVersion": DATASET_VERSION,
            "generatorVersion": GENERATOR_VERSION,
            "generatorSeed": GENERATOR_SEED,
            "generatedAt": GENERATED_AT,
            "syntheticSite": {"lat": round(site[1], ROUND_DIGITS), "lng": round(site[0], ROUND_DIGITS)},
        }
        features.append(
            {
                "type": "Feature",
                "id": prabhag_id,
                "properties": properties,
                "geometry": {"type": "Polygon", "coordinates": [closed_coordinates(polygon)]},
            }
        )
        rows.append(
            {
                **{key: value for key, value in properties.items() if key != "syntheticSite"},
                "geometry": polygon_wkt(polygon),
                "isActive": True,
            }
        )

    expected_area = (MAX_LNG - MIN_LNG) * (MAX_LAT - MIN_LAT)
    if abs(total_area - expected_area) > 1e-6:
        raise RuntimeError(f"Generated area {total_area} does not cover extent area {expected_area}")

    collection = {
        "type": "FeatureCollection",
        "name": "Seewik deterministic synthetic Nandurbar prabhag boundaries",
        "metadata": {
            "datasetVersion": DATASET_VERSION,
            "generatorVersion": GENERATOR_VERSION,
            "generatorSeed": GENERATOR_SEED,
            "generatedAt": GENERATED_AT,
            "prabhagCount": PRABHAG_COUNT,
            "resolutionQuality": "SYNTHETIC_BOUNDARY",
            "requiresCitizenConfirmation": True,
            "sourceStatus": "UNSOURCED",
            "reviewStatus": "REVIEW_PENDING",
            "extent": {
                "west": MIN_LNG,
                "south": MIN_LAT,
                "east": MAX_LNG,
                "north": MAX_LAT,
                "source": EXTENT_SOURCE,
                "osmCityNode": OSM_CITY_NODE,
                "quality": "OSM_CITY_SEARCH_EXTENT_NOT_MUNICIPAL_BOUNDARY",
            },
            "warning": "Synthetic development data. Not official prabhag or municipal geometry.",
        },
        "features": features,
    }
    geojson = json.dumps(collection, ensure_ascii=False, indent=2, sort_keys=True) + "\n"
    ndjson = "".join(json.dumps(row, ensure_ascii=False, sort_keys=True) + "\n" for row in rows)
    checksum = hashlib.sha256(geojson.encode("utf-8")).hexdigest() + "  synthetic-boundaries-v0.1.geojson\n"
    return geojson, ndjson, checksum


def check_or_write(check: bool) -> None:
    expected = dict(zip((GEOJSON_PATH, NDJSON_PATH, CHECKSUM_PATH), build_outputs(), strict=True))
    if check:
        stale = [path.name for path, content in expected.items() if not path.exists() or path.read_text() != content]
        if stale:
            raise SystemExit("Generated boundary artifacts are stale: " + ", ".join(stale))
        print(f"PASS: {PRABHAG_COUNT} deterministic polygons; committed artifacts match generator")
        return
    for path, content in expected.items():
        path.write_text(content)
    print(f"Generated {PRABHAG_COUNT} deterministic synthetic polygons")
    print(expected[CHECKSUM_PATH].strip())


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true", help="verify committed artifacts without changing them")
    args = parser.parse_args()
    check_or_write(args.check)


if __name__ == "__main__":
    try:
        main()
    except (RuntimeError, OSError) as error:
        print(f"ERROR: {error}", file=sys.stderr)
        raise SystemExit(1) from error
