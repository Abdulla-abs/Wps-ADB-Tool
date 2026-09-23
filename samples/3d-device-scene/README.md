# 3D Device Scene 示例文件

目录中有两个 glTF Binary（`.glb`）示例：

- `Device-Lab-Scene.glb`：桌面实验台环境，包含四个可拾取/可绑定位：`device_slot_1` 至 `device_slot_4`。
- `Demo-Phone-Asset.glb`：独立低多边形手机模型，适合演示 Position / Rotation / Scale。

## 导入和体验 Transform

1. 在顶部点 `+ Import`，选择 `Device-Lab-Scene.glb`，导入新场景。
2. 顶部交互模式切到 `EDITING`。
3. Inspector 的 `Scene Assets` 区域点 `+ Import Asset`，选择 `Demo-Phone-Asset.glb`。
4. 导入后在 Asset 列表中选中手机。
5. Inspector 出现 `Asset Transform`，可编辑 Position、Rotation、Scale；视口中的 Gizmo 也可直接拖动。

Transform 是 3D 物体的变换属性：

- **Position**：位置（X/Y/Z）
- **Rotation**：旋转角度（X/Y/Z）
- **Scale**：缩放比例（X/Y/Z）

Scene 环境模型是只读的；当前实现只允许对独立导入的 Asset 使用 Transform。若选中的是平台/槽位，Inspector 会显示固定对象提示，不会出现 Asset Transform 控件。

示例 Asset 初始位于场景原点。可先将 Position X 设为 `-1` 左右，让手机移动到第二个平台附近，再试旋转和缩放。

重新生成示例文件：

```powershell
node scripts/generate_scene_samples.mjs
```
