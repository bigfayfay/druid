
## 目标
需要为 druid-ak 定义一些启动参数，以调优； 给出jvm参数的调优建议(方向+参数+示例)；
依据jvm的堆内存结构+业务场景； 先给出总体方向思路， 然后再集合当前已有数据，展开分析。给出建议

## 业务背景
druid-ak是依据alibaba的druid框架为依据， 做SQL解析得到SQL模板(预编译语句)的工具jar包。
1. c程序 将druid-ak.jar在一个环境中启动， 然后多线程调用（1-10个线程）；
2. 业务场景为数据库的操作语句审计
3. 业务数据量在1-2亿/每天（1100-2350 SQL/s），以及3-5亿/每天(3500-5800 SQL/s)； 最大的情况可能会有10亿/每天(11600 SQL/s)；依据设备硬件配置


### 要求
1. gc的时间尽量短，因为审计操作日志是实时的。
2. 线程启动个数 依据操作系统的内存容量设置，8G-1线程，16G-4线程，32G-4线程，64G-128G启10线程；  内存还有其他程序使用， 不是单独给druid-ak的。

## 内存参数计算
是否可以依据 SQL/s + 并发数 + SQL长度 来估算JVM内存参数？
eg: (2500 |5000 |7500 |10000) sql/s) * (1-4-10) * 500 avgLen

## 监控数据
注意： a.采集的监控数据，内存+cpu使用不够准确。有很大一部分比例是数据采集给druid-ak.jar喂数据使用了（PerfTestCofig,不间断的从审计表中查询数据，其中还有Queue队列缓存）。
疑似某重复性很高的SQL，可能 Xmx=128m, 曾经处理过 7000 avgLen  9亿/天 ； 只不过有一些性能问题，影响到了审计。。。

监控数据， 不完全等价实际场景，是通过java工程引入后，从审计表中获取SQL后，调用durid-ak来解析的。 有性能+资源的额外损耗，仅供参考。
汇总数据：都是这只的4个线程
TotalCalls表示调用的次数，Failure表示失败的次数，Success表示成功的次数
ExecSpeed=1945.87 ops/s 表示 执行总次数/执行总时间, 其中方法执行总时间，有并发执行的情况；
ElapsedSpeed=7758.28 ops/s 表示 执行总次数/启动后过去的总时间，同样性能损耗；

处理结果：
[Failure   ] n=15423                | elapsed=25780004            ms | exec=3042                ms | avgLen=5790   | speed(exec/elapsed)=  5069.3/     0.6 ops/s | concurrency=0.0
n=15423， 表示执行的次数，avgLen=5790，表示平均SQL长度，concurrency=0.0，表示并发数（通过exec/elapsed估算的）


### node62
TotalCalls=200008614 | Failure=15423(0.01%) Success=199864083(99.93%) NonSupport=129108(0.06%) EXCEPTION=0(0.00%) | ExecSpeed=1945.87 ops/s | ElapsedSpeed=7758.28 ops/s
OS CPU[cores=16, system=36.0%, jvm_process=25.1%, processCpuTime=104440630ms]
OS Memory(total=62.8 GB, free=1.2 GB) | Swap(total=0 B, free=0 B)
JVM Heap(Xms/Xmx): 1.0 GB/2.0 GB | Heap(used/committed): 1.1 GB/1.7 GB | NonHeap(used): 88.2 MB | Free/Total: 606.9 MB/1.7 GB
JVM Heap Pools: G1 Eden Space(used=680.0 MB, committed=1.1 GB, max=N/A) G1 Survivor Space(used=3.0 MB, committed=3.0 MB, max=N/A) G1 Old Gen(used=422.2 MB, committed=634.0 MB, max=2.0 GB)
JVM Threads: live=22, peak=24, daemon=21
[Failure   ] n=15423                | elapsed=25780004            ms | exec=3042                ms | avgLen=5790   | speed(exec/elapsed)=  5069.3/     0.6 ops/s | concurrency=0.0
<50μs     : 560                  (  3.6%) [>20K     ops/s]
50-67μs   : 438                  (  2.8%) [15K-20K  ops/s]
67-100μs  : 1061                 (  6.9%) [10K-15K  ops/s]
100-133μs : 6336                 ( 41.1%) [7.5K-10K ops/s]
133-200μs : 3973                 ( 25.8%) [5K-7.5K  ops/s]
200-400μs : 2634                 ( 17.1%) [2.5K-5K  ops/s]
400μs-1ms : 152                  (  1.0%) [1K-2.5K  ops/s]
1-2ms     : 67                   (  0.4%) [500-1K   ops/s]
>2ms      : 202                  (  1.3%) [<500     ops/s]
[Success   ] n=199864083            | elapsed=25780004            ms | exec=102782963           ms | avgLen=377    | speed(exec/elapsed)=  1944.5/  7752.7 ops/s | concurrency=4.0
<50μs     : 51593939             ( 25.8%) [>20K     ops/s]
50-67μs   : 1569                 (  0.0%) [15K-20K  ops/s]
67-100μs  : 3931                 (  0.0%) [10K-15K  ops/s]
100-133μs : 7295                 (  0.0%) [7.5K-10K ops/s]
133-200μs : 12866                (  0.0%) [5K-7.5K  ops/s]
200-400μs : 28431                (  0.0%) [2.5K-5K  ops/s]
400μs-1ms : 140571256            ( 70.3%) [1K-2.5K  ops/s]
1-2ms     : 7156894              (  3.6%) [500-1K   ops/s]
>2ms      : 487902               (  0.2%) [<500     ops/s]

### node203
TotalCalls=499961592 | Failure=193867(0.04%) Success=498912121(99.79%) NonSupport=855604(0.17%) EXCEPTION=0(0.00%) | ExecSpeed=1810.53 ops/s | ElapsedSpeed=7212.37 ops/s
OS CPU[cores=16, system=37.0%, jvm_process=25.2%, processCpuTime=278996810ms]
OS Memory(total=62.8 GB, free=755.6 MB) | Swap(total=0 B, free=0 B)
JVM Heap(Xms/Xmx): 1.0 GB/2.0 GB | Heap(used/committed): 1.4 GB/1.9 GB | NonHeap(used): 88.8 MB | Free/Total: 471.0 MB/1.9 GB
JVM Heap Pools: G1 Eden Space(used=860.0 MB, committed=1.1 GB, max=N/A) G1 Survivor Space(used=47.0 MB, committed=47.0 MB, max=N/A) G1 Old Gen(used=562.0 MB, committed=747.0 MB, max=2.0 GB)
JVM Threads: live=24, peak=24, daemon=22
[Failure   ] n=193867               | elapsed=69320002            ms | exec=23383               ms | avgLen=293    | speed(exec/elapsed)=  8290.8/     2.8 ops/s | concurrency=0.0
<50μs     : 71528                ( 36.9%) [>20K     ops/s]
50-67μs   : 8154                 (  4.2%) [15K-20K  ops/s]
67-100μs  : 29891                ( 15.4%) [10K-15K  ops/s]
100-133μs : 62154                ( 32.1%) [7.5K-10K ops/s]
133-200μs : 8911                 (  4.6%) [5K-7.5K  ops/s]
200-400μs : 1990                 (  1.0%) [2.5K-5K  ops/s]
400μs-1ms : 10683                (  5.5%) [1K-2.5K  ops/s]
1-2ms     : 414                  (  0.2%) [500-1K   ops/s]
>2ms      : 142                  (  0.1%) [<500     ops/s]
[Success   ] n=498912123            | elapsed=69320002            ms | exec=276117894           ms | avgLen=405    | speed(exec/elapsed)=  1806.9/  7197.2 ops/s | concurrency=4.0
<50μs     : 96230721             ( 19.3%) [>20K     ops/s]
50-67μs   : 2489                 (  0.0%) [15K-20K  ops/s]
67-100μs  : 1843                 (  0.0%) [10K-15K  ops/s]
100-133μs : 699                  (  0.0%) [7.5K-10K ops/s]
133-200μs : 469                  (  0.0%) [5K-7.5K  ops/s]
200-400μs : 163488               (  0.0%) [2.5K-5K  ops/s]
400μs-1ms : 381000786            ( 76.4%) [1K-2.5K  ops/s]
1-2ms     : 20133843             (  4.0%) [500-1K   ops/s]
>2ms      : 1377785              (  0.3%) [<500     ops/s]


### node107
TotalCalls=499930000 | Failure=674256(0.13%) Success=161742112(32.35%) NonSupport=337513632(67.51%) EXCEPTION=0(0.00%) | ExecSpeed=3165.03 ops/s | ElapsedSpeed=10611.97 ops/s
OS CPU[cores=16, system=46.0%, jvm_process=5.6%, processCpuTime=160728830ms]
OS Memory(total=62.7 GB, free=4.3 GB) | Swap(total=0 B, free=0 B)
JVM Heap(Xms/Xmx): 1.0 GB/2.0 GB | Heap(used/committed): 513.2 MB/2.0 GB | NonHeap(used): 85.5 MB | Free/Total: 1.5 GB/2.0 GB
JVM Heap Pools: G1 Eden Space(used=41.0 MB, committed=1.2 GB, max=N/A) G1 Survivor Space(used=7.0 MB, committed=7.0 MB, max=N/A) G1 Old Gen(used=465.2 MB, committed=748.0 MB, max=2.0 GB)
JVM Threads: live=24, peak=24, daemon=22
[Failure   ] n=674256               | elapsed=47110004            ms | exec=122302              ms | avgLen=4579   | speed(exec/elapsed)=  5513.0/    14.3 ops/s | concurrency=0.0
<50μs     : 1321                 (  0.2%) [>20K     ops/s]
50-67μs   : 757                  (  0.1%) [15K-20K  ops/s]
67-100μs  : 17278                (  2.6%) [10K-15K  ops/s]
100-133μs : 234355               ( 34.8%) [7.5K-10K ops/s]
133-200μs : 259790               ( 38.5%) [5K-7.5K  ops/s]
200-400μs : 144147               ( 21.4%) [2.5K-5K  ops/s]
400μs-1ms : 16099                (  2.4%) [1K-2.5K  ops/s]
1-2ms     : 299                  (  0.0%) [500-1K   ops/s]
>2ms      : 210                  (  0.0%) [<500     ops/s]
[Success   ] n=161742112            | elapsed=47110004            ms | exec=157736377           ms | avgLen=289    | speed(exec/elapsed)=  1025.4/  3433.3 ops/s | concurrency=3.3
<50μs     : 7372027              (  4.6%) [>20K     ops/s]
50-67μs   : 972                  (  0.0%) [15K-20K  ops/s]
67-100μs  : 674                  (  0.0%) [10K-15K  ops/s]
100-133μs : 255                  (  0.0%) [7.5K-10K ops/s]
133-200μs : 270                  (  0.0%) [5K-7.5K  ops/s]
200-400μs : 85                   (  0.0%) [2.5K-5K  ops/s]
400μs-1ms : 103742427            ( 64.1%) [1K-2.5K  ops/s]
1-2ms     : 49112252             ( 30.4%) [500-1K   ops/s]
>2ms      : 1513150              (  0.9%) [<500     ops/s]


### node135
TotalCalls=500006462 | Failure=42456(0.01%) Success=499964006(99.99%) NonSupport=0(0.00%) EXCEPTION=0(0.00%) | ExecSpeed=1966.89 ops/s | ElapsedSpeed=7842.01 ops/s
OS CPU[cores=16, system=37.5%, jvm_process=25.5%, processCpuTime=259893840ms]
OS Memory(total=62.8 GB, free=882.7 MB) | Swap(total=0 B, free=0 B)
JVM Heap(Xms/Xmx): 1.0 GB/2.0 GB | Heap(used/committed): 923.3 MB/1.8 GB | NonHeap(used): 90.0 MB | Free/Total: 909.7 MB/1.8 GB
JVM Heap Pools: G1 Eden Space(used=257.0 MB, committed=1.0 GB, max=N/A) G1 Survivor Space(used=3.0 MB, committed=3.0 MB, max=N/A) G1 Old Gen(used=664.3 MB, committed=803.0 MB, max=2.0 GB)
JVM Threads: live=22, peak=23, daemon=21
[Failure   ] n=42456                | elapsed=63760002            ms | exec=5726                ms | avgLen=1607   | speed(exec/elapsed)=  7414.0/     0.7 ops/s | concurrency=0.0
<67μs     : 6303                 ( 14.8%) [>15K     ops/s]
67-100μs  : 10279                ( 24.2%) [10-15K   ops/s]
100-133μs : 9882                 ( 23.3%) [7.5-10K  ops/s]
133-200μs : 13173                ( 31.0%) [5-7.5K   ops/s]
200-333μs : 2153                 (  5.1%) [3-5K     ops/s]
333-500μs : 305                  (  0.7%) [2-3K     ops/s]
500-667μs : 203                  (  0.5%) [1.5-2K   ops/s]
667μs-1ms : 72                   (  0.2%) [1-1.5K   ops/s]
1-2ms     : 63                   (  0.1%) [500-1K   ops/s]
>2ms      : 23                   (  0.1%) [<500     ops/s]
[Success   ] n=499964008            | elapsed=63760002            ms | exec=254205387           ms | avgLen=198    | speed(exec/elapsed)=  1966.8/  7841.3 ops/s | concurrency=4.0
<67μs     : 96398904             ( 19.3%) [>15K     ops/s]
67-100μs  : 67956                (  0.0%) [10-15K   ops/s]
100-133μs : 95597                (  0.0%) [7.5-10K  ops/s]
133-200μs : 104077               (  0.0%) [5-7.5K   ops/s]
200-333μs : 15384                (  0.0%) [3-5K     ops/s]
333-500μs : 114234330            ( 22.8%) [2-3K     ops/s]
500-667μs : 222715319            ( 44.5%) [1.5-2K   ops/s]
667μs-1ms : 51719131             ( 10.3%) [1-1.5K   ops/s]
1-2ms     : 13493130             (  2.7%) [500-1K   ops/s]
>2ms      : 1120180              (  0.2%) [<500     ops/s]


### node160
TotalCalls=499956069 | Failure=114489(0.02%) Success=498732700(99.76%) NonSupport=1108880(0.22%) EXCEPTION=0(0.00%) | ExecSpeed=1960.57 ops/s | ElapsedSpeed=5385.14 ops/s
OS CPU[cores=16, system=43.8%, jvm_process=25.6%, processCpuTime=267722810ms]
OS Memory(total=62.8 GB, free=625.7 MB) | Swap(total=0 B, free=0 B)
JVM Heap(Xms/Xmx): 1.0 GB/2.0 GB | Heap(used/committed): 578.2 MB/2.0 GB | NonHeap(used): 86.2 MB | Free/Total: 1.4 GB/2.0 GB
JVM Heap Pools: G1 Eden Space(used=273.0 MB, committed=884.0 MB, max=N/A) G1 Survivor Space(used=38.0 MB, committed=38.0 MB, max=N/A) G1 Old Gen(used=267.2 MB, committed=1.1 GB, max=2.0 GB)
JVM Threads: live=24, peak=25, daemon=22
[Failure   ] n=114489               | elapsed=92840003            ms | exec=38729               ms | avgLen=26584  | speed(exec/elapsed)=  2956.1/     1.2 ops/s | concurrency=0.0
<50μs     : 92                   (  0.1%) [>20K     ops/s]
50-67μs   : 546                  (  0.5%) [15K-20K  ops/s]
67-100μs  : 3409                 (  3.0%) [10K-15K  ops/s]
100-133μs : 37968                ( 33.2%) [7.5K-10K ops/s]
133-200μs : 30209                ( 26.4%) [5K-7.5K  ops/s]
200-400μs : 7529                 (  6.6%) [2.5K-5K  ops/s]
400μs-1ms : 32382                ( 28.3%) [1K-2.5K  ops/s]
1-2ms     : 2124                 (  1.9%) [500-1K   ops/s]
>2ms      : 230                  (  0.2%) [<500     ops/s]
[Success   ] n=498732702            | elapsed=92840002            ms | exec=254966208           ms | avgLen=512    | speed(exec/elapsed)=  1956.1/  5372.0 ops/s | concurrency=2.7
<50μs     : 94114421             ( 18.9%) [>20K     ops/s]
50-67μs   : 2130                 (  0.0%) [15K-20K  ops/s]
67-100μs  : 1469                 (  0.0%) [10K-15K  ops/s]
100-133μs : 583                  (  0.0%) [7.5K-10K ops/s]
133-200μs : 423                  (  0.0%) [5K-7.5K  ops/s]
200-400μs : 2524727              (  0.5%) [2.5K-5K  ops/s]
400μs-1ms : 396680911            ( 79.5%) [1K-2.5K  ops/s]
1-2ms     : 4582485              (  0.9%) [500-1K   ops/s]
>2ms      : 825553               (  0.2%) [<500     ops/s]

### node116
TotalCalls=499958050 | Failure=651086(0.13%) Success=253530711(50.71%) NonSupport=245776253(49.16%) EXCEPTION=0(0.00%) | ExecSpeed=3077.17 ops/s | ElapsedSpeed=10327.58 ops/s
OS CPU[cores=16, system=56.4%, jvm_process=22.1%, processCpuTime=169124430ms]
OS Memory(total=62.8 GB, free=20.2 GB) | Swap(total=0 B, free=0 B)
JVM Heap(Xms/Xmx): 1.0 GB/2.0 GB | Heap(used/committed): 1.2 GB/2.0 GB | NonHeap(used): 90.3 MB | Free/Total: 826.2 MB/2.0 GB
JVM Heap Pools: G1 Eden Space(used=761.0 MB, committed=1.2 GB, max=N/A) G1 Survivor Space(used=27.0 MB, committed=27.0 MB, max=N/A) G1 Old Gen(used=432.3 MB, committed=758.0 MB, max=2.0 GB)
JVM Threads: live=23, peak=23, daemon=21
[Failure   ] n=651086               | elapsed=48410002            ms | exec=191180              ms | avgLen=8579   | speed(exec/elapsed)=  3405.6/    13.4 ops/s | concurrency=0.0
<67μs     : 165795               ( 25.5%) [>15K     ops/s]
67-100μs  : 149342               ( 22.9%) [10-15K   ops/s]
100-133μs : 62169                (  9.5%) [7.5-10K  ops/s]
133-200μs : 23646                (  3.6%) [5-7.5K   ops/s]
200-333μs : 37776                (  5.8%) [3-5K     ops/s]
333-500μs : 37689                (  5.8%) [2-3K     ops/s]
500-667μs : 94990                ( 14.6%) [1.5-2K   ops/s]
667μs-1ms : 71542                ( 11.0%) [1-1.5K   ops/s]
1-2ms     : 7485                 (  1.1%) [500-1K   ops/s]
>2ms      : 652                  (  0.1%) [<500     ops/s]
[Success   ] n=253530712            | elapsed=48410002            ms | exec=162229453           ms | avgLen=778    | speed(exec/elapsed)=  1562.8/  5237.2 ops/s | concurrency=3.4
<67μs     : 10432869             (  4.1%) [>15K     ops/s]
67-100μs  : 724                  (  0.0%) [10-15K   ops/s]
100-133μs : 257                  (  0.0%) [7.5-10K  ops/s]
133-200μs : 135                  (  0.0%) [5-7.5K   ops/s]
200-333μs : 47                   (  0.0%) [3-5K     ops/s]
333-500μs : 31762635             ( 12.5%) [2-3K     ops/s]
500-667μs : 145844444            ( 57.5%) [1.5-2K   ops/s]
667μs-1ms : 58898262             ( 23.2%) [1-1.5K   ops/s]
1-2ms     : 5972152              (  2.4%) [500-1K   ops/s]
>2ms      : 619187               (  0.2%) [<500     ops/s]



现场环境验证监控数据为：
[Failure   ] sqlTotal=175523                | elapsed=11460002            ms | exec=10240               ms | avgLen=1338   | speed(exec/elapsed)= 17140.8/    15.3 ops/s | concurrency=0.0                                       
[Success   ] sqlTotal=50728047             | elapsed=11460002            ms | exec=31572046            ms | avgLen=140    | speed(exec/elapsed)=  1606.7/  4426.5 ops/s | concurrency=2.8  


