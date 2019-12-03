# server code adapted from https://www.techbeamers.com/python-tutorial-write-multithreaded-python-server/
import socket
import struct
import time
import sys
import random
import numpy as np
import threading
from socketserver import ThreadingMixIn
import os

import gym
from gym import spaces

from stable_baselines.common.policies import MlpLnLstmPolicy
from stable_baselines.common.vec_env import DummyVecEnv
from stable_baselines import PPO2

import tensorflow as tf
os.environ['CUDA_VISIBLE_DEVICES'] = '-1'

packets_handled = 0

BUTTON_BIT_UP = 1
BUTTON_BIT_DOWN = 2
BUTTON_BIT_LEFT = 4
BUTTON_BIT_RIGHT = 8
BUTTON_BIT_A = 16
BUTTON_BIT_B = 32
BUTTON_BIT_C = 64
BUTTON_BIT_D = 128
BUTTON_BIT_E = 256
BUTTON_BIT_F = 512 # not used in vs ruleset

def conv2obv(board, queue, hold, incoming):
    queue_conv = []
    for x in range(6):
        queue_conv.append(int(queue[x] + 1))
    return {"board": np.frombuffer(board, np.uint8),
            "queue": queue_conv,
            "hold": hold,
            "incoming": incoming}

# because sockets suck
def recvfull(sockObj, size):
    read = b''
    left = size
    while (len(read) != size):
        last_size = len(read)
        read += sockObj.recv(left)
        left -= len(read) - last_size
    return read

class TetrisEnv(gym.Env):
    metadata = {'render.modes': ['human']}

    def __init__(self, port):
        #print("init start")
        # arrow keys, a, b, c, d, e
        self.action_space = spaces.MultiDiscrete([5, 2, 2, 2, 2, 2])
        self.observation_space = spaces.Dict({"board": spaces.Box(low=0, high=1, dtype=np.uint8, shape=(10,17)),
                                              "queue": spaces.MultiDiscrete([9, 9, 9, 9, 9, 9]),
                                              "hold": spaces.Discrete(9),
                                              "incoming": spaces.Discrete(255)
                                              })
        self.initialized = False
        self.done = False
        # start waiting for the client to connect
        self.server = socket.socket(socket.AF_INET, socket.SOCK_STREAM) 
        self.server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1) 
        self.server.bind(('localhost', port)) 
        self.server.listen(1)
        (self.conn, (a, b)) = self.server.accept() 
        #print("init done")
        return

    def step(self, action):
        if not self.done:
            #print('step start')
            self.initialized = True
            reward = 0
            self.done = False

            # encode action into input bits
            input_bits = 0

            # encode arrow keys
            arrow_actions = [0, BUTTON_BIT_UP, BUTTON_BIT_DOWN, BUTTON_BIT_LEFT, BUTTON_BIT_RIGHT]
            input_bits |= arrow_actions[action[0]]

            # encode buttons
            button_actions = [BUTTON_BIT_A, BUTTON_BIT_B, BUTTON_BIT_C, BUTTON_BIT_D, BUTTON_BIT_E]
            for x in range(6):
                if x == 0:
                    continue
                if action[x]:
                    input_bits |= button_actions[x - 1]

            # send it to the client
            self.conn.send(struct.pack('>I', input_bits))

            # get what the client observes
            magic = struct.unpack('>I', recvfull(self.conn, 4))[0]
            board = recvfull(self.conn, 170)
            queue = recvfull(self.conn, 6)
            hold = int(recvfull(self.conn, 1)[0])
            incoming = int(recvfull(self.conn, 1)[0])
            obv = conv2obv(board, queue, hold, incoming)

            if magic == 1: # game ended
                #print('step hit done path')
                self.done = True
                status = struct.unpack('>I', recvfull(self.conn, 4))[0]
                if status == 3: # MOVE / this agent won
                    reward = 1000
                elif status == 10: # GAMEOVER / this agent lost
                    reward = -1000
            #print('step done')
            return obv, reward, self.done, {}
        else:
            print('step, but already done')
            return {'board': np.empty((10,17), dtype=np.uint8), 'queue': [8, 8, 8, 8, 8, 8], 'hold': 8, 'incoming': 0}, 0, True, {}

    def reset(self):
        # this check is in place because reset gets called right after init, before any observation is recieved
        if self.initialized:
            self.done = False
        # generate a blank observation
        return {'board': np.empty((10,17), dtype=np.uint8), 'queue': [8, 8, 8, 8, 8, 8], 'hold': 8, 'incoming': 0}

    def render(self, mode='human', close=False):
        # TODO
        # do i even need to implement this? nullpomino makes replays...
        return

def train_agent(agent, bot_id):
    while True:
        agent.learn(1000000)
        #agent.learn(1000)
        agent.save("agent" + str(bot_id))

if __name__ == '__main__':
    env1 = TetrisEnv(1337)
    env1 = gym.wrappers.FlattenObservation(env1)
    env1 = DummyVecEnv([lambda: env1])
    env2 = TetrisEnv(1338)
    env2 = gym.wrappers.FlattenObservation(env2)
    env2 = DummyVecEnv([lambda: env2])

    policy_kwargs = dict(act_fun=tf.nn.tanh, net_arch=[128, 'lstm', 32])
    agent1 = PPO2(MlpLnLstmPolicy, env1, verbose=1, nminibatches=1, policy_kwargs=policy_kwargs)
    agent2 = PPO2(MlpLnLstmPolicy, env2, verbose=1, nminibatches=1, policy_kwargs=policy_kwargs)

    '''
    episode_count = 100
    reward1 = 0
    reward2 = 0
    done = False
    start_time = time.time()
    counter = 0 

    for i in range(episode_count):
        ob1 = env1.reset()
        ob2 = env2.reset()
        while True:
            action = agent1.act(ob1, reward1, done)
            ob1, reward1, done, _ = env1.step(action)
            action = agent2.act(ob2, reward2, done)
            ob2, reward2, done, _ = env2.step(action)
            counter += 1
            if (time.time() - start_time) > 1:
                print("\r", counter / (time.time() - start_time), "actions per second", end="")
                counter = 0
                start_time = time.time()
            if done:
                break
    '''

    t1 = threading.Thread(target=train_agent, args=(agent1,0,))
    t2 = threading.Thread(target=train_agent, args=(agent2,1,))
    t1.start()
    t2.start()
    t1.join()
    t2.join()

    # Close the env and write monitor result info to disk
    env1.close()
    env2.close()