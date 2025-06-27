目前仅支持fabric 1.20.6

----------------------------------------------------------------------------

本模组功能为对一定范围内区块进行直接的复制粘贴,优点是速度极快,大范围复制粘贴也几乎不会产生卡顿.worldedit的是逐个方块复制粘贴,若选区较大则会产生严重卡顿.

适用于可破坏地图的场地重置,相比重新读档或worldedit schematic复制粘贴速度快得多.20x20区块也可以1秒内完成复制粘贴.

局限性在于无法进行任何y轴上的偏移,也无法在xz轴上进行非16的整数倍格数的偏移.

----------------------------------------------------------------------------

指令格式:

复制

/chunks copy x1 z1 x2 z2

粘贴

/chunks paste x3 z3

----------------------------------------------------------------------------

所有坐标都是区块坐标,值必须为整数.

获取区块坐标:

方法1:可以通过F3调试查看

方法2:输入/chunks copy然后按空格,查看指令的自动补全,可获取你当前所在区块的区块坐标.

----------------------------------------------------------------------------

源区域 x1 z1和x2 z2 是斜对角选区,只要是斜对角就行.由于是完整区块复制,y轴范围默认为最低点到最高点,且不可更改.

目标区域 x3 z3 偏移方式原版/clone指令类似,即定义目标区域的西北方向较低（即在各轴上坐标值最小）的点的坐标.

区块边界可按F3+G查看.

----------------------------------------------------------------------------
----------------------------------------------------------------------------

Currently only supports Fabric 1.20.6

This mod's function is to directly copy and paste chunks within a certain range. Its advantage is extremely high speed; large-scale copy and paste operations will cause almost no lag. WorldEdit copies and pastes block by block, which can cause severe lag if the selected area is large.

It is suitable for resetting venues on destructible maps, being much faster than reloading a save or using WorldEdit schematics for copying andpasting. Even a 20x20 chunk area can be copied and pasted within 1 second.

Its limitations are that it cannot perform any offset on the y-axis, nor can it perform offsets on the xz-axes that are not integer multiples of 16 blocks.

----------------------------------------------------------------------------

Command Format:

Copy
/chunks copy x1 z1 x2 z2

Paste
/chunks paste x3 z3

----------------------------------------------------------------------------

All coordinates are chunk coordinates, and the values must be integers.

How to get chunk coordinates:

Method 1: You can view them through the F3 debug screen.

Method 2: Type /chunks copy then press the spacebar, and look at the command's auto-completion(suggestion) to get the chunk coordinates of your current location.

----------------------------------------------------------------------------

The source area x1 z1 and x2 z2 define a diagonal selection; any two opposite corners will work. Since it's a full chunk copy, the y-axis range defaults from the world bottom to the world top and cannot be changed.

The destination area x3 z3.The offset method is similar to the vanilla /clone command, which means you define the coordinates of the north-west, lower corner of the destination area (i.e., the point with the smallest coordinate values on each axis).

Chunk borders can be viewed by pressing F3+G.