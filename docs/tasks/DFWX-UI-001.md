# DFWX-UI-001：动画根因和性能证据

状态：待执行
前置：TEST-001

## 目标

先找出开关过快、抽帧、图标闪烁和展开收起僵硬的真实原因，再统一修复，不靠单控件特殊参数掩盖状态链问题。

## 必须先取证

setter 调用、同值调用、cancel/restart、attach/detach、layout 重排、主线程耗时、Choreographer 帧间隔、gfxinfo jank 和对照控件。

## 禁止

未证明根因前不改 `LumaSwitch` duration、物理参数或依赖；不恢复手表测试；不引入未经批准动画库；不同时改多个专题。

## 验收

有原始数据、根因判断、最小修复、修复前后数字和手机 release 验收；动效统一且无状态回写闪烁。
