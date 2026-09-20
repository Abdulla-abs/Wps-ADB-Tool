// WpsAdbTool 3D Renderer Host Spike — Three.js Scene & Bridge

(function () {
    console.log("[ThreeSpike] Initializing renderer script...");

    // 1. Bridge helper for JS -> Kotlin
    const messageQueue = [];
    function sendToKotlin(type, payload = {}) {
        const msg = JSON.stringify({ type, payload, timestamp: Date.now() });
        if (typeof window.cefQuery === 'function') {
            try {
                window.cefQuery({
                    request: msg,
                    persistent: false,
                    onSuccess: function (response) {
                        // Ack from Kotlin
                    },
                    onFailure: function (error_code, error_message) {
                        console.error("[ThreeSpike] Bridge send error:", error_code, error_message);
                    }
                });
            } catch (e) {
                console.error("[ThreeSpike] Exception in cefQuery:", e);
            }
        } else {
            messageQueue.push(msg);
        }
    }

    // Flush queued messages once bridge is ready
    function flushQueue() {
        if (typeof window.cefQuery === 'function' && messageQueue.length > 0) {
            while (messageQueue.length > 0) {
                const msg = messageQueue.shift();
                window.cefQuery({ request: msg, persistent: false, onSuccess: () => {}, onFailure: () => {} });
            }
        }
    }
    setInterval(flushQueue, 200);

    // 2. Query WebGL Capabilities
    let webglInfo = { supported: false, vendor: "unknown", renderer: "unknown", version: "unknown" };
    try {
        const testCanvas = document.createElement("canvas");
        const gl = testCanvas.getContext("webgl2") || testCanvas.getContext("webgl");
        if (gl) {
            webglInfo.supported = true;
            webglInfo.version = gl.getParameter(gl.VERSION);
            const debugInfo = gl.getExtension('WEBGL_debug_renderer_info');
            if (debugInfo) {
                webglInfo.vendor = gl.getParameter(debugInfo.UNMASKED_VENDOR_WEBGL);
                webglInfo.renderer = gl.getParameter(debugInfo.UNMASKED_RENDERER_WEBGL);
            } else {
                webglInfo.vendor = gl.getParameter(gl.VENDOR);
                webglInfo.renderer = gl.getParameter(gl.RENDERER);
            }
        }
    } catch (e) {
        console.warn("[ThreeSpike] Error probing WebGL:", e);
    }
    console.log("[ThreeSpike] WebGL Info:", webglInfo);

    // 3. Scene, Camera, Renderer Setup
    const container = document.getElementById("canvas-container");
    const width = container.clientWidth || window.innerWidth;
    const height = container.clientHeight || window.innerHeight;

    const scene = new THREE.Scene();
    scene.background = new THREE.Color(0x0c0e11);
    scene.fog = new THREE.FogExp2(0x0c0e11, 0.035);

    const camera = new THREE.PerspectiveCamera(45, width / height, 0.1, 100);
    const initialCameraPos = new THREE.Vector3(0, 4, 9);
    camera.position.copy(initialCameraPos);
    camera.lookAt(0, 1, 0);

    let renderer;
    try {
        renderer = new THREE.WebGLRenderer({ antialias: true, alpha: false, powerPreference: "high-performance" });
        renderer.setSize(width, height);
        renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
        renderer.shadowMap.enabled = true;
        renderer.shadowMap.type = THREE.PCFSoftShadowMap;
        container.appendChild(renderer.domElement);
    } catch (e) {
        console.error("[ThreeSpike] Failed to create WebGLRenderer:", e);
        sendToKotlin("WEBGL_ERROR", { error: e.message });
        return;
    }

    // 4. Lighting & Environment
    const ambientLight = new THREE.AmbientLight(0xffffff, 0.5);
    scene.add(ambientLight);

    const dirLight = new THREE.DirectionalLight(0xffffff, 0.8);
    dirLight.position.set(5, 10, 7);
    dirLight.castShadow = true;
    dirLight.shadow.mapSize.width = 1024;
    dirLight.shadow.mapSize.height = 1024;
    scene.add(dirLight);

    const blueLight = new THREE.PointLight(0x00e5ff, 1.2, 12);
    blueLight.position.set(-4, 3, 2);
    scene.add(blueLight);

    // Floor Grid
    const gridHelper = new THREE.GridHelper(20, 20, 0x1f2937, 0x111827);
    gridHelper.position.y = 0;
    scene.add(gridHelper);

    // 5. Interactive Device Models
    const pickableObjects = [];
    const devicesGroup = new THREE.Group();
    scene.add(devicesGroup);

    function createDeviceMesh(id, name, width, height, depth, color, posX, posZ) {
        const group = new THREE.Group();
        group.position.set(posX, height / 2 + 0.05, posZ);

        // Body
        const bodyGeo = new THREE.BoxGeometry(width, height, depth);
        const bodyMat = new THREE.MeshStandardMaterial({
            color: 0x1e293b,
            roughness: 0.3,
            metalness: 0.8
        });
        const bodyMesh = new THREE.Mesh(bodyGeo, bodyMat);
        bodyMesh.castShadow = true;
        bodyMesh.receiveShadow = true;
        group.add(bodyMesh);

        // Screen face
        const screenGeo = new THREE.PlaneGeometry(width * 0.9, height * 0.9);
        const screenMat = new THREE.MeshStandardMaterial({
            color: color,
            emissive: color,
            emissiveIntensity: 0.4,
            roughness: 0.1,
            metalness: 0.1
        });
        const screenMesh = new THREE.Mesh(screenGeo, screenMat);
        screenMesh.position.z = depth / 2 + 0.005;
        group.add(screenMesh);

        // Stand / Base
        const standGeo = new THREE.BoxGeometry(width * 0.7, 0.1, depth * 2);
        const standMat = new THREE.MeshStandardMaterial({ color: 0x334155, metalness: 0.9, roughness: 0.2 });
        const standMesh = new THREE.Mesh(standGeo, standMat);
        standMesh.position.y = -height / 2;
        group.add(standMesh);

        group.userData = {
            id: id,
            name: name,
            status: "online",
            originalColor: color,
            currentColor: color,
            bodyMesh: bodyMesh,
            screenMesh: screenMesh,
            screenMat: screenMat
        };

        // Make root pickable
        bodyMesh.userData = group.userData;
        screenMesh.userData = group.userData;

        pickableObjects.push(bodyMesh, screenMesh);
        devicesGroup.add(group);
        return group;
    }

    const dev1 = createDeviceMesh("device_pixel_8", "Google Pixel 8 (USB)", 0.9, 1.8, 0.15, 0x10b981, -2.5, 0);
    const dev2 = createDeviceMesh("device_s24", "Samsung Galaxy S24 (WiFi)", 1.0, 2.0, 0.15, 0x38bdf8, 0, 0);
    const dev3 = createDeviceMesh("device_tablet", "Pixel Tablet (Bench)", 2.4, 1.6, 0.18, 0xa855f7, 2.8, 0);

    // 6. Camera Orbit & Drag Controls
    let isDragging = false;
    let previousMousePosition = { x: 0, y: 0 };
    let spherical = { radius: 9, theta: 0, phi: Math.PI / 4 };
    let target = new THREE.Vector3(0, 1, 0);

    function updateCameraFromSpherical() {
        spherical.phi = Math.max(0.1, Math.min(Math.PI / 2 - 0.02, spherical.phi));
        spherical.radius = Math.max(2, Math.min(25, spherical.radius));

        camera.position.x = target.x + spherical.radius * Math.sin(spherical.phi) * Math.sin(spherical.theta);
        camera.position.y = target.y + spherical.radius * Math.cos(spherical.phi);
        camera.position.z = target.z + spherical.radius * Math.sin(spherical.phi) * Math.cos(spherical.theta);
        camera.lookAt(target);
    }
    updateCameraFromSpherical();

    window.addEventListener("mousedown", (e) => {
        isDragging = true;
        previousMousePosition = { x: e.clientX, y: e.clientY };
    });

    window.addEventListener("mousemove", (e) => {
        if (isDragging) {
            const deltaX = e.clientX - previousMousePosition.x;
            const deltaY = e.clientY - previousMousePosition.y;

            spherical.theta -= deltaX * 0.008;
            spherical.phi -= deltaY * 0.008;
            updateCameraFromSpherical();

            previousMousePosition = { x: e.clientX, y: e.clientY };
        } else {
            handleRaycast(e.clientX, e.clientY, false);
        }
    });

    window.addEventListener("mouseup", (e) => {
        if (isDragging) {
            const moveDist = Math.hypot(e.clientX - previousMousePosition.x, e.clientY - previousMousePosition.y);
            isDragging = false;
            if (moveDist < 5) {
                // Was a click
                handleRaycast(e.clientX, e.clientY, true);
            }
        }
    });

    window.addEventListener("wheel", (e) => {
        spherical.radius += e.deltaY * 0.01;
        updateCameraFromSpherical();
    });

    // 7. Raycasting & Picking
    const raycaster = new THREE.Raycaster();
    const mouse = new THREE.Vector2();
    let hoveredObject = null;
    let selectedObject = null;

    const hudHovered = document.getElementById("hud-hovered");
    const hudSelected = document.getElementById("hud-selected");

    function handleRaycast(clientX, clientY, isClick) {
        mouse.x = (clientX / window.innerWidth) * 2 - 1;
        mouse.y = -(clientY / window.innerHeight) * 2 + 1;

        raycaster.setFromCamera(mouse, camera);
        const intersects = raycaster.intersectObjects(pickableObjects, false);

        if (intersects.length > 0) {
            const hit = intersects[0].object;
            const data = hit.userData;

            if (isClick) {
                selectedObject = data;
                hudSelected.innerHTML = `<b>Selected:</b> <span class="${data.status}">${data.name} [${data.id}]</span>`;
                console.log("[ThreeSpike] Object Selected:", data.id);
                sendToKotlin("OBJECT_SELECTED", {
                    id: data.id,
                    name: data.name,
                    status: data.status,
                    colorHex: "#" + data.currentColor.toString(16).padStart(6, '0')
                });
            } else {
                if (hoveredObject !== data) {
                    if (hoveredObject) {
                        hoveredObject.screenMat.emissiveIntensity = 0.4;
                    }
                    hoveredObject = data;
                    hoveredObject.screenMat.emissiveIntensity = 0.85;
                    hudHovered.innerHTML = `<b>Hovered:</b> ${data.name}`;
                    sendToKotlin("OBJECT_HOVERED", { id: data.id, name: data.name });
                }
            }
        } else {
            if (!isClick && hoveredObject) {
                hoveredObject.screenMat.emissiveIntensity = 0.4;
                hoveredObject = null;
                hudHovered.innerHTML = `<b>Hovered:</b> None`;
                sendToKotlin("OBJECT_UNHOVERED", {});
            }
        }
    }

    // 8. Kotlin -> JS Public API
    let isRotating = true;
    window.wpsRenderer = {
        setObjectColor: function (id, hexColor) {
            console.log("[ThreeSpike] Set object color:", id, hexColor);
            devicesGroup.children.forEach(group => {
                if (group.userData && group.userData.id === id) {
                    const colorNum = parseInt(hexColor.replace("#", "0x"), 16);
                    group.userData.currentColor = colorNum;
                    group.userData.screenMat.color.setHex(colorNum);
                    group.userData.screenMat.emissive.setHex(colorNum);
                }
            });
            sendToKotlin("COLOR_UPDATED", { id, hexColor });
        },

        toggleRotation: function () {
            isRotating = !isRotating;
            console.log("[ThreeSpike] Rotation toggled:", isRotating);
            sendToKotlin("ROTATION_TOGGLED", { isRotating });
            return isRotating;
        },

        resetCamera: function () {
            spherical = { radius: 9, theta: 0, phi: Math.PI / 4 };
            target.set(0, 1, 0);
            updateCameraFromSpherical();
            sendToKotlin("CAMERA_RESET", {});
        },

        setDeviceStatus: function (id, status) {
            console.log("[ThreeSpike] Set device status:", id, status);
            devicesGroup.children.forEach(group => {
                if (group.userData && group.userData.id === id) {
                    group.userData.status = status;
                    if (status === "offline") {
                        group.userData.screenMat.color.setHex(0x334155);
                        group.userData.screenMat.emissive.setHex(0x1e293b);
                        group.userData.screenMat.emissiveIntensity = 0.1;
                    } else {
                        group.userData.screenMat.color.setHex(group.userData.currentColor);
                        group.userData.screenMat.emissive.setHex(group.userData.currentColor);
                        group.userData.screenMat.emissiveIntensity = 0.4;
                    }
                }
            });
            if (selectedObject && selectedObject.id === id) {
                hudSelected.innerHTML = `<b>Selected:</b> <span class="${status}">${selectedObject.name} [${id}]</span>`;
            }
            sendToKotlin("STATUS_UPDATED", { id, status });
        },

        loadGLBTest: function () {
            console.log("[ThreeSpike] Testing GLTFLoader availability...");
            if (typeof THREE.GLTFLoader === 'function') {
                sendToKotlin("GLTF_LOADER_AVAILABLE", { available: true });
            } else {
                sendToKotlin("GLTF_LOADER_AVAILABLE", { available: false });
            }
        },

        simulatePick: function (id) {
            console.log("[ThreeSpike] Simulating pick for:", id);
            devicesGroup.children.forEach(group => {
                if (group.userData && group.userData.id === id) {
                    const data = group.userData;
                    selectedObject = data;
                    hudSelected.innerHTML = `<b>Selected:</b> <span class="${data.status}">${data.name} [${data.id}]</span>`;
                    sendToKotlin("OBJECT_SELECTED", {
                        id: data.id,
                        name: data.name,
                        status: data.status,
                        colorHex: "#" + data.currentColor.toString(16).padStart(6, '0')
                    });
                }
            });
        }
    };

    // 9. Window Resize
    function onResize() {
        const w = window.innerWidth;
        const h = window.innerHeight;
        camera.aspect = w / h;
        camera.updateProjectionMatrix();
        renderer.setSize(w, h);
        sendToKotlin("SCENE_RESIZED", { width: w, height: h });
    }
    window.addEventListener("resize", onResize);

    // 10. Animation Loop
    let lastTime = performance.now();
    let frameCount = 0;
    const hudFps = document.getElementById("hud-fps");

    function animate(time) {
        requestAnimationFrame(animate);

        // Rotation
        if (isRotating) {
            devicesGroup.rotation.y += 0.005;
        }

        renderer.render(scene, camera);

        // FPS calculation
        frameCount++;
        if (time - lastTime >= 1000) {
            const fps = Math.round((frameCount * 1000) / (time - lastTime));
            hudFps.innerHTML = `<b>FPS:</b> ${fps}`;
            frameCount = 0;
            lastTime = time;
        }
    }
    requestAnimationFrame(animate);

    // 11. Notify Kotlin Scene is Ready
    setTimeout(() => {
        sendToKotlin("SCENE_READY", {
            webgl: webglInfo,
            initialObjects: [
                { id: "device_pixel_8", name: "Google Pixel 8 (USB)" },
                { id: "device_s24", name: "Samsung Galaxy S24 (WiFi)" },
                { id: "device_tablet", name: "Pixel Tablet (Bench)" }
            ]
        });
        window.wpsRenderer.loadGLBTest();
    }, 100);

})();
