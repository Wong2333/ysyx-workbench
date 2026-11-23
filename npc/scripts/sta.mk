# 面积与时序（yosys-sta + iEDA）
#
#   make -C npc sta
#
# 具体目标（syn / sta / fix-fanout）由 $(YOSYS_STA_HOME)/Makefile.common 提供，
# 这里只负责配置：设计名、输入 RTL、时钟端口/频率、工艺库。
#   CLK_FREQ_MHZ=800  与 yosys-sta 默认的 500MHz 不同，本地按 800MHz 约束评估。

YOSYS_STA_HOME ?= /home/wjm/yosys-sta

DESIGN = ysyx_26030092
RTL_FILES = $(CORE)
export VERILOG_INCLUDE_DIRS = $(CURDIR)/resources/include
export CLK_PORT_NAME = clock
CLK_FREQ_MHZ = 800

-include $(YOSYS_STA_HOME)/Makefile.common

PDK_NAME = nangate45
