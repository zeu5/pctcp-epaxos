import sys
import os
import re

DEBUG_FILE="debug.log"

def remove_prefix(string, prefix):
    if string.startswith(prefix):
        return string[len(prefix):]
    return string

class State:
    def __init__(self):
        self.events = []
        self.next_event = None
    
    def empty(self):
        return len(self.events) == 0 and self.next_event is None

class Event:
    def __init__(self):
        self.id = ""
        self.msg = ""
        self.msgType = ""
        self.sender = ""
        self.recv = ""

class CommitMsg:
    def __init__(self):
        self.leader = ""
        self.replica = ""
        self.instance = ""
        self.deps = []

def parse(file):
    states = []
    state_lines = []
    for line in file.readlines():
        line = line.strip()
        if line == "Update from Target System:":
            if len(state_lines) != 0:
                state = parse_state(state_lines)
                if not state.empty():
                    states.append(state)
            state_lines = []
        state_lines.append(line)
    return states

def parse_state(lines):
    new_state = State()
    for line in lines:
        if line.startswith("Next Event:"):
            start = line.find("packetsend")
            new_state.next_event = parse_event(line[start:])
        elif line.find("packetsend") != -1:
            start = line.find("packetsend")
            new_state.events.append(parse_event(line[start:]))
    return new_state

def parse_event(line):
    event = Event()
    p = re.match(r'(?P<header>packetsend)\s(?P<tid>tid=-?[0-9]+)\s(?P<event>Event=\{.*\})\s.*', line)
    e = None
    msg = None
    if p is not None:
        e = re.match(r'\{(?P<msg>msg=&.*\{.*\},)\s(?P<others>.*)\}', remove_prefix(p.group('event'), "Event="))
    if e is not None:
        for props in e.group('others').split(','):
            props = props.strip()
            [key, value] = props.split('=')
            if key == "verb":
                event.msgType = value
            if key == "sendNode":
                event.sender = value
            if key == "recvNode":
                event.recv = value
            if key == "eventId":
                event.id = value
        if (event.msgType == "Commit" or event.msgType == "CommitShort"):
            event.msg = parse_commitmsg(remove_prefix(e.group('msg'),'msg=&'))
        else:
            event.msg = remove_prefix(e.group('msg'),'msg=&')
    return event

def parse_commitmsg(msg):
    r = re.match(r'(?P<name>\w+\.\w+)\{(?P<fields>.*)\}', msg)
    if r is not None:
        fields = r.group('fields')
        c = CommitMsg()
        l_re = re.search(r'LeaderId:(?P<leader>\d+)', fields)
        if l_re is not None:
            c.leader = l_re.group('leader')
        r_re = re.search(r'Replica:(?P<replica>\d+)', fields)
        if r_re is not None:
            c.replica = r_re.group('replica')
        i_re = re.search(r'Instance:(?P<instance>\d+)', fields)
        if i_re is not None:
            c.instance = i_re.group('instance')
        d_re = re.search(r'Deps:\[5\]int32\{(?P<deps>.*)\}', fields)
        if d_re is not None:
            for d in d_re.group('deps').split(','):
                c.deps.append(int(d.strip()))
        return c
    return None

def conflict(states):
    commits = []
    for state in states:
        for event in state.events:
            if event.msgType == "Commit" or event.msgType == "CommitShort":
                commits.append(event.msg)

    # for c in commits:
    #     if c.leader != c.replica:
    #         return True
    # return False

    instances = {}
    for c in commits:
        if c.replica not in instances:
            instances[c.replica] = {}
        if c.instance not in instances[c.replica]:
            instances[c.replica][c.instance] = c.deps
        if c.deps != instances[c.replica][c.instance]:
            return True
    return False

def check_path(d):
    if os.path.isdir(d):
        with open(os.path.join(d,DEBUG_FILE)) as f:
            states = parse(f)
            if len(states) != 0:
                if conflict(states):
                    return True
            else:
                emptyruns += 1
    return False

def get_going(directory):
    errors = []
    emptyruns = 0
    for d in os.listdir(directory):
        if os.path.isdir(os.path.join(directory,d)):
            with open(os.path.join(directory,d,DEBUG_FILE)) as f:
                states = parse(f)
                if len(states) != 0:
                    if conflict(states):
                        errors.append(d)
                else:
                    emptyruns += 1
    print("Error instances")
    print(errors)

    print("Empty runs :",emptyruns)

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Please provide the state directory")
        sys.exit(1)
    if not os.path.isdir(sys.argv[1]):
        print("Argument should be a directory")
        sys.exit(1)
    
    get_going(sys.argv[1])



# duplicated = 0
# empty = 0
# cwd = os.getcwd()
# maxlen = 0
# for d in os.listdir(cwd):
#     filename = os.path.join(cwd, d, "path")
#     if os.path.exists(filename):
#         with open(filename) as f:
#             lines = f.readlines()
#             if len(lines) == 0:
#                 empty += 1
#             elif lines[-2].strip() == "Duplicated path.":
#                 duplicated +=  1
#             else:
#                 if len(lines) - 1 > maxlen:
#                     maxlen = len(lines) - 1

