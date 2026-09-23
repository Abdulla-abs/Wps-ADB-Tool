import { test, describe } from "node:test";
import assert from "node:assert";
import * as THREE from "three";
import { DeviceVisualState } from "../src/visual/DeviceVisualState.ts";
import type { DeviceVisualDescriptor } from "../../renderer-contract/scene-bridge-contract.ts";

describe("DeviceVisualState Unit Tests", () => {
  test("applies status color and emissive state to mesh without recreating geometry", () => {
    const visualState = new DeviceVisualState();

    const geometry = new THREE.BoxGeometry(1, 1, 1);
    const material = new THREE.MeshStandardMaterial({
      color: 0xcccccc,
      emissive: 0x000000,
      emissiveIntensity: 0,
    });
    const mesh = new THREE.Mesh(geometry, material);
    mesh.name = "phone_slot";

    const objectsById = new Map<string, THREE.Object3D>([["phone_slot", mesh]]);

    const descriptor: DeviceVisualDescriptor = {
      objectId: "phone_slot",
      deviceIdentity: "DEV_01",
      displayName: "Pixel 8",
      status: "ONLINE",
      connectionType: "USB",
      visual: {
        statusColorHex: "#10b981",
        isEmissive: true,
        emissiveIntensity: 0.8,
        badgeText: "ONLINE",
        isDimmed: false,
      },
    };

    visualState.applyVisualDescriptors([descriptor], objectsById);

    // Verify material modified in-place
    assert.strictEqual(material.emissiveIntensity, 0.8);
    assert.strictEqual(material.emissive.getHexString(), "10b981");
    assert.strictEqual(material.color.getHexString(), "10b981");
    assert.strictEqual(material.transparent, false);

    // Transition to OFFLINE and dimmed
    const offlineDesc: DeviceVisualDescriptor = {
      ...descriptor,
      status: "OFFLINE",
      visual: {
        ...descriptor.visual,
        statusColorHex: "#64748b",
        isEmissive: false,
        emissiveIntensity: 0,
        isDimmed: true,
      },
    };

    visualState.applyVisualDescriptors([offlineDesc], objectsById);
    assert.strictEqual(material.emissiveIntensity, 0);
    assert.strictEqual(material.color.getHexString(), "64748b");
    assert.strictEqual(material.transparent, true);
    assert.strictEqual(material.opacity, 0.45);
  });

  test("applies status color to non-emissive MeshBasicMaterial", () => {
    const visualState = new DeviceVisualState();
    const basicMat = new THREE.MeshBasicMaterial({ color: 0xffffff });
    const mesh = new THREE.Mesh(new THREE.BoxGeometry(1, 1, 1), basicMat);
    const objectsById = new Map<string, THREE.Object3D>([["basic_slot", mesh]]);

    visualState.applyVisualDescriptors([
      {
        objectId: "basic_slot",
        deviceIdentity: "DEV_02",
        displayName: "Basic Device",
        status: "ONLINE",
        connectionType: "USB",
        visual: { statusColorHex: "#3b82f6", isEmissive: false, emissiveIntensity: 0, badgeText: "USB", isDimmed: false },
      },
    ], objectsById);

    assert.strictEqual(basicMat.color.getHexString(), "3b82f6");

    // Clear restores original white color
    const dummyScene = new THREE.Scene();
    visualState.clear(dummyScene);
    assert.strictEqual(basicMat.color.getHexString(), "ffffff");
  });


  test("manages selection highlight helper on scene", () => {
    const visualState = new DeviceVisualState();
    const scene = new THREE.Scene();

    const mesh = new THREE.Mesh(new THREE.BoxGeometry(1, 1, 1), new THREE.MeshBasicMaterial());
    const objectsById = new Map<string, THREE.Object3D>([["device_a", mesh]]);

    assert.strictEqual(scene.children.length, 0);

    // Select object
    visualState.updateSelection("device_a", objectsById, scene);
    assert.strictEqual(scene.children.length, 1); // BoxHelper added

    // Deselect object
    visualState.updateSelection(null, objectsById, scene);
    assert.strictEqual(scene.children.length, 0); // BoxHelper removed
  });

  test("updates the selection helper bounds after its object moves", () => {
    const visualState = new DeviceVisualState();
    const scene = new THREE.Scene();
    const mesh = new THREE.Mesh(new THREE.BoxGeometry(1, 1, 1), new THREE.MeshBasicMaterial());
    scene.add(mesh);
    const objectsById = new Map<string, THREE.Object3D>([["asset_a", mesh]]);

    visualState.updateSelection("asset_a", objectsById, scene);
    const helper = scene.children.find((child) => child instanceof THREE.BoxHelper) as THREE.BoxHelper;
    const before = helper.geometry.getAttribute("position").getX(0);
    mesh.position.x = 4;
    mesh.updateMatrixWorld(true);

    visualState.updateSelectionHighlights();

    const after = helper.geometry.getAttribute("position").getX(0);
    assert.strictEqual(after, before + 4);
    visualState.clear(scene);
  });
});
