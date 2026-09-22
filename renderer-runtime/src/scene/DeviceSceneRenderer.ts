import * as THREE from "three";

export interface DeviceSceneRendererOptions {
  container: HTMLElement;
  clearColor?: number;
  initialFov?: number;
  initialPosition?: { x: number; y: number; z: number };
  initialTarget?: { x: number; y: number; z: number };
}

/**
 * DeviceSceneRenderer
 * Manages Three.js Scene, PerspectiveCamera, WebGLRenderer, base lighting,
 * staging grid, container resize observation, and the render loop.
 */
export class DeviceSceneRenderer {
  private readonly container: HTMLElement;
  private readonly scene: THREE.Scene;
  private readonly camera: THREE.PerspectiveCamera;
  private readonly renderer: THREE.WebGLRenderer;
  private readonly contentGroup: THREE.Group;
  private readonly gridHelper: THREE.GridHelper;
  private readonly resizeObserver: ResizeObserver;

  private animationFrameId: number | null = null;
  private isDisposed = false;
  private onBeforeRenderCallbacks: Array<() => void> = [];

  constructor(options: DeviceSceneRendererOptions) {
    this.container = options.container;

    // 1. Scene setup
    this.scene = new THREE.Scene();
    const clearColor = options.clearColor ?? 0x0c0e11;
    this.scene.background = new THREE.Color(clearColor);
    this.scene.fog = new THREE.FogExp2(clearColor, 0.035);

    // 2. Camera setup
    const width = Math.max(1, this.container.clientWidth);
    const height = Math.max(1, this.container.clientHeight);
    const fov = options.initialFov ?? 45;
    this.camera = new THREE.PerspectiveCamera(fov, width / height, 0.1, 1000);

    const initPos = options.initialPosition ?? { x: 0, y: 4, z: 9 };
    const initTarget = options.initialTarget ?? { x: 0, y: 1, z: 0 };
    this.camera.position.set(initPos.x, initPos.y, initPos.z);
    this.camera.lookAt(initTarget.x, initTarget.y, initTarget.z);

    // 3. WebGLRenderer setup
    this.renderer = new THREE.WebGLRenderer({
      antialias: true,
      alpha: false,
      powerPreference: "high-performance",
    });
    this.renderer.setSize(width, height);
    this.renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2));
    this.renderer.shadowMap.enabled = true;
    this.renderer.shadowMap.type = THREE.PCFSoftShadowMap;

    this.container.appendChild(this.renderer.domElement);

    // 4. Lighting setup
    const ambientLight = new THREE.AmbientLight(0xffffff, 0.6);
    this.scene.add(ambientLight);

    const dirLight = new THREE.DirectionalLight(0xffffff, 1.0);
    dirLight.position.set(5, 10, 7);
    dirLight.castShadow = true;
    dirLight.shadow.mapSize.width = 1024;
    dirLight.shadow.mapSize.height = 1024;
    dirLight.shadow.camera.near = 0.5;
    dirLight.shadow.camera.far = 50;
    this.scene.add(dirLight);

    const blueLight = new THREE.PointLight(0x00e5ff, 0.8, 15);
    blueLight.position.set(-4, 3, 2);
    this.scene.add(blueLight);

    // 5. Staging grid helper
    this.gridHelper = new THREE.GridHelper(20, 20, 0x374151, 0x1f2937);
    this.gridHelper.position.y = 0;
    this.scene.add(this.gridHelper);

    // 6. Dynamic content group (holding environments, models, etc.)
    this.contentGroup = new THREE.Group();
    this.contentGroup.name = "__contentGroup__";
    this.scene.add(this.contentGroup);

    // 7. Auto resize observation
    this.resizeObserver = new ResizeObserver(() => {
      this.handleResize();
    });
    this.resizeObserver.observe(this.container);

    // 8. Start render loop
    this.startRenderLoop();
  }

  private handleResize(): void {
    if (this.isDisposed) return;
    const width = Math.max(1, this.container.clientWidth);
    const height = Math.max(1, this.container.clientHeight);

    this.camera.aspect = width / height;
    this.camera.updateProjectionMatrix();
    this.renderer.setSize(width, height);
  }

  private startRenderLoop = (): void => {
    const loop = () => {
      if (this.isDisposed) return;

      for (let i = 0; i < this.onBeforeRenderCallbacks.length; i++) {
        try {
          this.onBeforeRenderCallbacks[i]();
        } catch (e) {
          console.error("[DeviceSceneRenderer] Error in beforeRender callback:", e);
        }
      }

      this.renderer.render(this.scene, this.camera);
      this.animationFrameId = requestAnimationFrame(loop);
    };
    this.animationFrameId = requestAnimationFrame(loop);
  };

  /**
   * Registers a callback executed immediately before each frame render.
   */
  addOnBeforeRender(callback: () => void): () => void {
    this.onBeforeRenderCallbacks.push(callback);
    return () => {
      this.onBeforeRenderCallbacks = this.onBeforeRenderCallbacks.filter((cb) => cb !== callback);
    };
  }

  getScene(): THREE.Scene {
    return this.scene;
  }

  getCamera(): THREE.PerspectiveCamera {
    return this.camera;
  }

  getRenderer(): THREE.WebGLRenderer {
    return this.renderer;
  }

  getContentGroup(): THREE.Group {
    return this.contentGroup;
  }

  getGridHelper(): THREE.GridHelper {
    return this.gridHelper;
  }

  getCanvas(): HTMLCanvasElement {
    return this.renderer.domElement;
  }

  dispose(): void {
    if (this.isDisposed) return;
    this.isDisposed = true;

    if (this.animationFrameId !== null) {
      cancelAnimationFrame(this.animationFrameId);
      this.animationFrameId = null;
    }

    this.resizeObserver.disconnect();
    this.onBeforeRenderCallbacks = [];

    // Dispose scene content
    this.contentGroup.clear();
    this.gridHelper.geometry.dispose();

    // Dispose renderer and remove canvas
    this.renderer.dispose();
    if (this.renderer.domElement.parentElement === this.container) {
      this.container.removeChild(this.renderer.domElement);
    }
  }
}
