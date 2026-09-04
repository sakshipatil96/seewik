export type PhotoCoordinates = {
  latitude: number;
  longitude: number;
};

const MAX_EXIF_BYTES = 256 * 1024;

function parseTiffGps(view: DataView, tiffStart: number, segmentEnd: number): PhotoCoordinates | null {
  if (tiffStart + 8 > segmentEnd) return null;
  const byteOrder = view.getUint16(tiffStart, false);
  if (byteOrder !== 0x4949 && byteOrder !== 0x4d4d) return null;
  const littleEndian = byteOrder === 0x4949;
  const readUint16 = (offset: number) => view.getUint16(offset, littleEndian);
  const readUint32 = (offset: number) => view.getUint32(offset, littleEndian);
  if (readUint16(tiffStart + 2) !== 42) return null;

  const firstIfd = tiffStart + readUint32(tiffStart + 4);
  if (firstIfd + 2 > segmentEnd) return null;
  const entryCount = readUint16(firstIfd);
  let gpsIfd = 0;
  for (let index = 0; index < entryCount; index += 1) {
    const entry = firstIfd + 2 + index * 12;
    if (entry + 12 > segmentEnd) return null;
    if (readUint16(entry) === 0x8825) {
      gpsIfd = tiffStart + readUint32(entry + 8);
      break;
    }
  }
  if (!gpsIfd || gpsIfd + 2 > segmentEnd) return null;

  const gpsEntryCount = readUint16(gpsIfd);
  let latitudeRef = '';
  let longitudeRef = '';
  let latitudeValues: number[] | null = null;
  let longitudeValues: number[] | null = null;
  const readRationals = (entry: number, count: number): number[] | null => {
    const valueOffset = tiffStart + readUint32(entry + 8);
    if (valueOffset < tiffStart || valueOffset + count * 8 > segmentEnd) return null;
    const values: number[] = [];
    for (let index = 0; index < count; index += 1) {
      const numerator = readUint32(valueOffset + index * 8);
      const denominator = readUint32(valueOffset + index * 8 + 4);
      if (!denominator) return null;
      values.push(numerator / denominator);
    }
    return values;
  };

  for (let index = 0; index < gpsEntryCount; index += 1) {
    const entry = gpsIfd + 2 + index * 12;
    if (entry + 12 > segmentEnd) return null;
    const tag = readUint16(entry);
    const type = readUint16(entry + 2);
    const count = readUint32(entry + 4);
    if ((tag === 1 || tag === 3) && type === 2 && count >= 1) {
      const reference = String.fromCharCode(view.getUint8(entry + 8)).toUpperCase();
      if (tag === 1) latitudeRef = reference;
      else longitudeRef = reference;
    } else if (tag === 2 && type === 5 && count === 3) {
      latitudeValues = readRationals(entry, count);
    } else if (tag === 4 && type === 5 && count === 3) {
      longitudeValues = readRationals(entry, count);
    }
  }

  if (!latitudeValues || !longitudeValues || !['N', 'S'].includes(latitudeRef) || !['E', 'W'].includes(longitudeRef)) return null;
  const decimal = ([degrees, minutes, seconds]: number[]) => degrees + minutes / 60 + seconds / 3600;
  const latitude = decimal(latitudeValues) * (latitudeRef === 'S' ? -1 : 1);
  const longitude = decimal(longitudeValues) * (longitudeRef === 'W' ? -1 : 1);
  if (!Number.isFinite(latitude) || !Number.isFinite(longitude) || Math.abs(latitude) > 90 || Math.abs(longitude) > 180) return null;
  return { latitude, longitude };
}

export function extractJpegPhotoCoordinates(buffer: ArrayBuffer): PhotoCoordinates | null {
  const view = new DataView(buffer);
  if (view.byteLength < 4 || view.getUint16(0, false) !== 0xffd8) return null;
  let offset = 2;
  while (offset + 4 <= view.byteLength) {
    if (view.getUint8(offset) !== 0xff) {
      offset += 1;
      continue;
    }
    const marker = view.getUint8(offset + 1);
    if (marker === 0xda || marker === 0xd9) break;
    if (marker >= 0xd0 && marker <= 0xd8) {
      offset += 2;
      continue;
    }
    const segmentLength = view.getUint16(offset + 2, false);
    const segmentEnd = offset + 2 + segmentLength;
    if (segmentLength < 2 || segmentEnd > view.byteLength) break;
    const payload = offset + 4;
    const isExif = marker === 0xe1
      && payload + 6 <= segmentEnd
      && view.getUint32(payload, false) === 0x45786966
      && view.getUint16(payload + 4, false) === 0;
    if (isExif) return parseTiffGps(view, payload + 6, segmentEnd);
    offset = segmentEnd;
  }
  return null;
}

export async function extractPhotoCoordinates(file: File): Promise<PhotoCoordinates | null> {
  const isJpeg = file.type === 'image/jpeg' || /\.jpe?g$/i.test(file.name);
  if (!isJpeg) return null;
  return extractJpegPhotoCoordinates(await file.slice(0, MAX_EXIF_BYTES).arrayBuffer());
}
