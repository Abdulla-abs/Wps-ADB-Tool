import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../samples/3d-device-scene");
fs.mkdirSync(root, { recursive: true });

function makeGlb(parts, outputPath) {
  const binaryParts = [];
  const bufferViews = [];
  const accessors = [];
  const meshes = [];
  const nodes = [];
  const materials = [];
  let byteOffset = 0;

  const align4 = () => {
    const padding = (4 - (byteOffset % 4)) % 4;
    if (padding) {
      binaryParts.push(Buffer.alloc(padding));
      byteOffset += padding;
    }
  };

  const addBuffer = (buffer, target) => {
    align4();
    const index = bufferViews.length;
    bufferViews.push({ buffer: 0, byteOffset, byteLength: buffer.length, target });
    binaryParts.push(buffer);
    byteOffset += buffer.length;
    return index;
  };

  const faceDefs = [
    [[0, 0, 1], [[-1, -1, 1], [1, -1, 1], [1, 1, 1], [-1, 1, 1]]],
    [[0, 0, -1], [[1, -1, -1], [-1, -1, -1], [-1, 1, -1], [1, 1, -1]]],
    [[1, 0, 0], [[1, -1, 1], [1, -1, -1], [1, 1, -1], [1, 1, 1]]],
    [[-1, 0, 0], [[-1, -1, -1], [-1, -1, 1], [-1, 1, 1], [-1, 1, -1]]],
    [[0, 1, 0], [[-1, 1, 1], [1, 1, 1], [1, 1, -1], [-1, 1, -1]]],
    [[0, -1, 0], [[-1, -1, -1], [1, -1, -1], [1, -1, 1], [-1, -1, 1]]],
  ];

  function addCube(part) {
    const [cx, cy, cz] = part.center;
    const [sx, sy, sz] = part.size;
    const positions = [];
    const normals = [];
    const indices = [];
    for (const [normal, corners] of faceDefs) {
      const base = positions.length / 3;
      for (const [x, y, z] of corners) {
        positions.push(cx + x * sx / 2, cy + y * sy / 2, cz + z * sz / 2);
        normals.push(...normal);
      }
      indices.push(base, base + 1, base + 2, base, base + 2, base + 3);
    }

    const positionBuffer = Buffer.from(new Float32Array(positions).buffer);
    const normalBuffer = Buffer.from(new Float32Array(normals).buffer);
    const indexBuffer = Buffer.from(new Uint16Array(indices).buffer);
    const positionView = addBuffer(positionBuffer, 34962);
    const normalView = addBuffer(normalBuffer, 34962);
    const indexView = addBuffer(indexBuffer, 34963);
    const positionAccessor = accessors.length;
    accessors.push({
      bufferView: positionView,
      componentType: 5126,
      count: positions.length / 3,
      type: "VEC3",
      min: [0, 1, 2].map((axis) => Math.min(...Array.from({ length: positions.length / 3 }, (_, i) => positions[i * 3 + axis]))),
      max: [0, 1, 2].map((axis) => Math.max(...Array.from({ length: positions.length / 3 }, (_, i) => positions[i * 3 + axis]))),
    });
    const normalAccessor = accessors.length;
    accessors.push({ bufferView: normalView, componentType: 5126, count: normals.length / 3, type: "VEC3" });
    const indexAccessor = accessors.length;
    accessors.push({ bufferView: indexView, componentType: 5123, count: indices.length, type: "SCALAR", min: [0], max: [23] });
    const materialIndex = materials.length;
    materials.push({
      name: `${part.name} Material`,
      pbrMetallicRoughness: {
        baseColorFactor: part.color,
        metallicFactor: part.metallic ?? 0.05,
        roughnessFactor: part.roughness ?? 0.62,
      },
    });
    const meshIndex = meshes.length;
    meshes.push({
      name: part.name,
      primitives: [{ attributes: { POSITION: positionAccessor, NORMAL: normalAccessor }, indices: indexAccessor, material: materialIndex, mode: 4 }],
    });
    const node = { name: part.name, mesh: meshIndex };
    if (part.objectId) node.extras = { objectId: part.objectId, isBindable: true };
    nodes.push(node);
  }

  for (const part of parts) addCube(part);
  const binary = Buffer.concat(binaryParts);
  const gltf = {
    asset: { version: "2.0", generator: "WpsAdbTool Scene Sample Generator" },
    scene: 0,
    scenes: [{ nodes: nodes.map((_, i) => i) }],
    nodes,
    meshes,
    materials,
    accessors,
    bufferViews,
    buffers: [{ byteLength: binary.length }],
  };
  let json = Buffer.from(JSON.stringify(gltf), "utf8");
  const jsonPadding = (4 - (json.length % 4)) % 4;
  if (jsonPadding) json = Buffer.concat([json, Buffer.alloc(jsonPadding, 0x20)]);
  const binPadding = (4 - (binary.length % 4)) % 4;
  const paddedBinary = binPadding ? Buffer.concat([binary, Buffer.alloc(binPadding)]) : binary;
  const totalLength = 12 + 8 + json.length + 8 + paddedBinary.length;
  const header = Buffer.alloc(12);
  header.writeUInt32LE(0x46546c67, 0);
  header.writeUInt32LE(2, 4);
  header.writeUInt32LE(totalLength, 8);
  const jsonHeader = Buffer.alloc(8);
  jsonHeader.writeUInt32LE(json.length, 0);
  jsonHeader.writeUInt32LE(0x4e4f534a, 4);
  const binHeader = Buffer.alloc(8);
  binHeader.writeUInt32LE(paddedBinary.length, 0);
  binHeader.writeUInt32LE(0x004e4942, 4);
  fs.writeFileSync(outputPath, Buffer.concat([header, jsonHeader, json, binHeader, paddedBinary]));
}

const sceneParts = [
  { name: "Lab Floor", center: [0, -2.12, 0], size: [12, 0.12, 8], color: [0.12, 0.18, 0.25, 1] },
  { name: "Desk Top", center: [0, -1.05, 0], size: [9, 0.22, 4.4], color: [0.42, 0.27, 0.17, 1], roughness: 0.82 },
  { name: "Desk Leg Left Front", center: [-3.85, -1.62, 1.7], size: [0.22, 1, 0.22], color: [0.18, 0.22, 0.28, 1], metallic: 0.65 },
  { name: "Desk Leg Right Front", center: [3.85, -1.62, 1.7], size: [0.22, 1, 0.22], color: [0.18, 0.22, 0.28, 1], metallic: 0.65 },
  { name: "Desk Leg Left Back", center: [-3.85, -1.62, -1.7], size: [0.22, 1, 0.22], color: [0.18, 0.22, 0.28, 1], metallic: 0.65 },
  { name: "Desk Leg Right Back", center: [3.85, -1.62, -1.7], size: [0.22, 1, 0.22], color: [0.18, 0.22, 0.28, 1], metallic: 0.65 },
  { name: "Back Panel", center: [0, 0.35, -1.95], size: [8.5, 2.6, 0.12], color: [0.08, 0.24, 0.31, 1] },
  { name: "Panel Accent", center: [0, 0.55, -1.86], size: [5.5, 0.06, 0.06], color: [0.12, 0.72, 0.72, 1], metallic: 0.3 },
  { name: "Slot 1 Platform", center: [-3, -0.91, 0.15], size: [1.15, 0.08, 1.25], color: [0.12, 0.72, 0.72, 1], objectId: "device_slot_1" },
  { name: "Slot 2 Platform", center: [-1, -0.91, 0.15], size: [1.15, 0.08, 1.25], color: [0.38, 0.63, 0.95, 1], objectId: "device_slot_2" },
  { name: "Slot 3 Platform", center: [1, -0.91, 0.15], size: [1.15, 0.08, 1.25], color: [0.95, 0.62, 0.22, 1], objectId: "device_slot_3" },
  { name: "Slot 4 Platform", center: [3, -0.91, 0.15], size: [1.15, 0.08, 1.25], color: [0.7, 0.42, 0.9, 1], objectId: "device_slot_4" },
  { name: "Slot 1 Label Block", center: [-3, -0.82, -0.62], size: [0.46, 0.035, 0.12], color: [0.12, 0.72, 0.72, 1] },
  { name: "Slot 2 Label Block", center: [-1, -0.82, -0.62], size: [0.46, 0.035, 0.12], color: [0.38, 0.63, 0.95, 1] },
  { name: "Slot 3 Label Block", center: [1, -0.82, -0.62], size: [0.46, 0.035, 0.12], color: [0.95, 0.62, 0.22, 1] },
  { name: "Slot 4 Label Block", center: [3, -0.82, -0.62], size: [0.46, 0.035, 0.12], color: [0.7, 0.42, 0.9, 1] },
];

const assetParts = [
  { name: "Phone Frame", center: [0, 0, 0], size: [0.92, 1.82, 0.12], color: [0.045, 0.065, 0.09, 1], metallic: 0.55, roughness: 0.3 },
  { name: "Phone Screen", center: [0, 0.02, 0.066], size: [0.79, 1.61, 0.014], color: [0.08, 0.48, 0.58, 1], metallic: 0.05, roughness: 0.24 },
  { name: "Screen Highlight", center: [0, 0.48, 0.075], size: [0.79, 0.68, 0.008], color: [0.12, 0.72, 0.74, 1], roughness: 0.22 },
  { name: "Earpiece", center: [0, 0.84, 0.077], size: [0.18, 0.018, 0.01], color: [0.52, 0.6, 0.68, 1], metallic: 0.6 },
  { name: "Camera Lens", center: [0.29, 0.84, 0.078], size: [0.045, 0.045, 0.018], color: [0.04, 0.08, 0.14, 1], metallic: 0.45, roughness: 0.2 },
  { name: "Power Button", center: [0.475, 0.35, 0], size: [0.035, 0.26, 0.06], color: [0.18, 0.78, 0.78, 1], metallic: 0.5 },
  { name: "Volume Button", center: [-0.475, 0.48, 0], size: [0.035, 0.32, 0.055], color: [0.4, 0.5, 0.62, 1], metallic: 0.5 },
];

makeGlb(sceneParts, path.join(root, "Device-Lab-Scene.glb"));
makeGlb(assetParts, path.join(root, "Demo-Phone-Asset.glb"));
console.log(`Generated sample GLBs in ${root}`);
