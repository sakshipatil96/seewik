import assert from 'node:assert/strict';
import test from 'node:test';

import { extractJpegPhotoCoordinates } from '../src/photoLocation.ts';

function writeEntry(view, offset, tag, type, count, value) {
  view.setUint16(offset, tag, true);
  view.setUint16(offset + 2, type, true);
  view.setUint32(offset + 4, count, true);
  view.setUint32(offset + 8, value, true);
}

function writeRational(view, offset, numerator, denominator = 1) {
  view.setUint32(offset, numerator, true);
  view.setUint32(offset + 4, denominator, true);
}

function createGpsJpeg(latitudeRef = 'N', longitudeRef = 'E') {
  const tiffLength = 128;
  const exifPayloadLength = 6 + tiffLength;
  const jpeg = new Uint8Array(2 + 2 + 2 + exifPayloadLength + 2);
  const view = new DataView(jpeg.buffer);

  jpeg.set([0xff, 0xd8, 0xff, 0xe1], 0);
  view.setUint16(4, exifPayloadLength + 2, false);
  jpeg.set([0x45, 0x78, 0x69, 0x66, 0x00, 0x00], 6);

  const tiff = 12;
  jpeg.set([0x49, 0x49], tiff);
  view.setUint16(tiff + 2, 42, true);
  view.setUint32(tiff + 4, 8, true);

  view.setUint16(tiff + 8, 1, true);
  writeEntry(view, tiff + 10, 0x8825, 4, 1, 26);
  view.setUint32(tiff + 22, 0, true);

  const gpsIfd = tiff + 26;
  view.setUint16(gpsIfd, 4, true);
  writeEntry(view, gpsIfd + 2, 0x0001, 2, 2, latitudeRef.charCodeAt(0));
  writeEntry(view, gpsIfd + 14, 0x0002, 5, 3, 80);
  writeEntry(view, gpsIfd + 26, 0x0003, 2, 2, longitudeRef.charCodeAt(0));
  writeEntry(view, gpsIfd + 38, 0x0004, 5, 3, 104);
  view.setUint32(gpsIfd + 50, 0, true);

  writeRational(view, tiff + 80, 21);
  writeRational(view, tiff + 88, 22);
  writeRational(view, tiff + 96, 12);
  writeRational(view, tiff + 104, 74);
  writeRational(view, tiff + 112, 14);
  writeRational(view, tiff + 120, 24);

  jpeg.set([0xff, 0xd9], jpeg.length - 2);
  return jpeg;
}

test('extracts Nandurbar GPS coordinates from JPEG EXIF data', () => {
  const coordinates = extractJpegPhotoCoordinates(createGpsJpeg().buffer);

  assert.ok(coordinates);
  assert.ok(Math.abs(coordinates.latitude - 21.37) < 0.000001);
  assert.ok(Math.abs(coordinates.longitude - 74.24) < 0.000001);
});

test('applies southern and western EXIF coordinate signs', () => {
  const coordinates = extractJpegPhotoCoordinates(createGpsJpeg('S', 'W').buffer);

  assert.ok(coordinates);
  assert.ok(coordinates.latitude < 0);
  assert.ok(coordinates.longitude < 0);
});

test('rejects files without valid JPEG EXIF GPS data', () => {
  assert.equal(extractJpegPhotoCoordinates(new Uint8Array([0x00, 0x01]).buffer), null);
  assert.equal(
    extractJpegPhotoCoordinates(new Uint8Array([0xff, 0xd8, 0xff, 0xe1, 0x00, 0x20]).buffer),
    null,
  );
});
