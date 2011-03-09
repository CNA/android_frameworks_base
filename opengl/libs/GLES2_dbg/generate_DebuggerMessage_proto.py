#!/usr/bin/python
# -*- coding: utf-8 -*-

import os

def generate_DebuggerMessage(output,lines,i):
	for line in lines:
		if line.find("API_ENTRY(") >= 0:
			line = line[line.find("(") + 1: line.find(")")] #extract GL function name
			output.write("\t\t%s = %d;\n" % (line, i))
			i += 1
	return i


if __name__ == "__main__":
	output = open("DebuggerMessage.proto",'w')
	output.write( """// do not edit; auto generated by generate_DebuggerMessage_proto.py
package GLESv2Debugger;

option optimize_for = LITE_RUNTIME;

message Message
{
\trequired int32 context_id = 1; // GL context id
\tenum Function
\t{
""")

	i = 0;
	
	lines = open("gl2_api.in").readlines()
	i = generate_DebuggerMessage(output, lines, i)
	output.write("\t\t// end of GL functions\n")
	
	#lines = open("gl2ext_api.in").readlines()
	#i = generate_DebuggerMessage(output, lines, i)
	#output.write("\t\t// end of GL EXT functions\n")
	
	output.write("\t\tACK = %d;\n" % (i))
	i += 1
	
	output.write("\t\tNEG = %d;\n" % (i))
	i += 1
	
	output.write("\t\tCONTINUE = %d;\n" % (i))
	i += 1
	
	output.write("\t\tSKIP = %d;\n" % (i))
	i += 1
	
	output.write("""\t}
\trequired Function function = 2 [default = NEG]; // type/function of message
\trequired bool has_next_message = 3;
\trequired bool expect_response = 4;
\toptional int32 ret = 5; // return value from previous GL call
\toptional int32 arg0 = 6; // args to GL call
\toptional int32 arg1 = 7;
\toptional int32 arg2 = 8;
\toptional int32 arg3 = 9;
\toptional int32 arg4 = 16;
\toptional int32 arg5 = 17;
\toptional int32 arg6 = 18;
\toptional int32 arg7 = 19;
\toptional int32 arg8 = 20;
\toptional bytes data = 10; // variable length data used for GL call
\toptional float time = 11; // timing of previous GL call (seconds)
}
""")

	output.close()
	
	os.system("aprotoc --cpp_out=src --java_out=client/src DebuggerMessage.proto")
	os.system(' mv -f "src/DebuggerMessage.pb.cc" "src/DebuggerMessage.pb.cpp" ')
