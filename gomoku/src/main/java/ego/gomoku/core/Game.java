package ego.gomoku.core;

import ego.gomoku.entity.CountData;
import ego.gomoku.entity.Counter;
import ego.gomoku.entity.Point;
import ego.gomoku.enumeration.Color;
import ego.gomoku.exception.TimeOutException;
import ego.gomoku.helper.ConsolePrinter;
import ego.gomoku.helper.WinChecker;

import java.util.List;

public class Game {

    private GameMap gameMap;

    private Counter counter = new Counter();

    private ConsolePrinter consolePrinter = new ConsolePrinter();

    private ComboProcessor comboProcessor = new ComboProcessor();

    private Color aiColor;

    private Config config;

    private Score score = new Score();

    private int currentLevel;

    public void init(Color[][] map, Config config) {
        gameMap = new GameMap(map);
        this.config = config;
    }

    public Result search(Color color, boolean randomBegin) {
        config.startTime = System.currentTimeMillis();
        //减少一秒的停止预估
        config.searchTimeOut -= 1000;

        Result result = new Result();
        Cache cache = new Cache(config, gameMap, counter);

        aiColor = color;
        if (WinChecker.win(gameMap.getMap()) != null) {
            return null;
        }
        //初始化
        consolePrinter.init(counter);
        score.init(gameMap, aiColor);
        comboProcessor.init(gameMap, score, counter, cache);

        //判断是否随机开局
        if (randomBegin) {
            Point point = BeginningProcessor.getBeginningRandomPoint(gameMap.getMap());
            if (point != null) {
                result.add(point, 0);
                return result;
            }
        }

        //只有一个扩展点的情形直接返回
        Analyzer data = new Analyzer(gameMap, color, gameMap.getNeighbor(), score, counter);
        List<Point> points = LevelProcessor.getExpandPoints(data);
        if (points.size() == 1) {
            result.add(points.get(0), 0);
            return result;
        }
        //初始胜利计算
        int comboLevel = config.comboDeep;

        for (int i = 1; i <= comboLevel; i += 2) {
            config.comboDeep = i;
            cache.clear();
            Point winTry = comboProcessor.canKill(color, config.comboDeep);

            if (winTry != null) {
                result.add(winTry, Integer.MAX_VALUE);
                return result;
            }
        }
        config.comboDeep = comboLevel;

        //逐个计算，并记录
        counter.allStep = points.size();
        int extreme = Integer.MIN_VALUE;
        try {
            for (int level = 4; level <= config.searchDeep; level += 2) {
                currentLevel = level;
                Result currentResult = new Result();
                for (Point point : points) {
                    setColor(point, color, Color.NULL, aiColor);
                    int value = 0;
                    value = dfsScore(level - 1, color.getOtherColor(), null, extreme);

                    counter.finishStep++;
                    consolePrinter.printInfo(point, value);

                    if (value >= extreme) {
                        extreme = value;
                        currentResult.add(point, value);

                        if (extreme == Integer.MAX_VALUE) {
                            currentResult.add(point, value);
                            break;
                        }
                    }
                    setColor(point, Color.NULL, color, aiColor);
                }
                result = currentResult;
                //如果已经用掉一半的时间，则停止
                if (System.currentTimeMillis() - config.startTime > config.searchTimeOut / 2) {
                    break;
                }
            }
        } catch (TimeOutException ignored) {
        }
        return result;
    }

    public CountData getCountData() {
        CountData data = new CountData();
        data.setAllStep(counter.allStep);
        data.setCount(counter.count);
        data.setFinishStep(counter.finishStep);
        return data;
    }

    private int dfsScore(int level, Color color, Integer parentMin, Integer parentMax) throws TimeOutException {
        //是否超时判断
        if (System.currentTimeMillis() - config.startTime > config.searchTimeOut) {
            throw new TimeOutException();
        }

        //斩杀剪枝
        if (level == config.searchDeep - 2) {
            if (comboProcessor.canKill(color, currentLevel * 2) != null) {
                return Integer.MAX_VALUE;
            }
        }
        if (level == config.searchDeep - 1) {
            if (comboProcessor.canKill(color, currentLevel * 2) != null) {
                return Integer.MIN_VALUE;
            }
        }
        //叶子分数计算
        if (level == 0) {
            return getScore();
        }
        //计算扩展节点
        Analyzer data = new Analyzer(gameMap, color, gameMap.getNeighbor(), score, counter);
        //输赢判定
        if (!data.getFiveAttack().isEmpty()) {
            if (color == aiColor) {
                return Integer.MAX_VALUE;
            }
            if (color == aiColor.getOtherColor()) {
                return Integer.MIN_VALUE;
            }
        }
        List<Point> points = LevelProcessor.getExpandPoints(data);
        //遍历扩展节点
        int extreme = color == aiColor ? Integer.MIN_VALUE : Integer.MAX_VALUE;
        for (Point point : points) {
            setColor(point, color, Color.NULL, aiColor);
            if (color == aiColor) {
                int value = dfsScore(level - 1, color.getOtherColor(), null, extreme);
                if (value > parentMin) {
                    setColor(point, Color.NULL, color, aiColor);
                    return value;
                }
                if (value > extreme) {
                    extreme = value;
                    //如果能赢了，则直接剪掉后面的情形
                    if (extreme == Integer.MAX_VALUE) {
                        setColor(point, Color.NULL, color, aiColor);
                        return extreme;
                    }
                }
            }
            if (color != aiColor) {
                int value = dfsScore(level - 1, color.getOtherColor(), extreme, null);
                if (value < parentMax) {
                    setColor(point, Color.NULL, color, aiColor);
                    return value;
                }
                if (value < extreme) {
                    extreme = value;
                    //如果已经输了，则直接剪掉后面的情形
                    if (extreme == Integer.MIN_VALUE) {
                        setColor(point, Color.NULL, color, aiColor);
                        return extreme;
                    }
                }
            }
            setColor(point, Color.NULL, color, aiColor);
        }
        return extreme;
    }

    private void setColor(Point point, Color color, Color forwardColor, Color aiColor) {
        score.setColor(point, color, forwardColor, aiColor);
        gameMap.setColor(point, color);
    }

    private int getScore() {
        counter.count++;
        return score.getMapScore();
    }
}
