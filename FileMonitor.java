package com.jnetzhou.utils.filemonitor;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.alibaba.fastjson.JSON;

/**
 * 
 * @author jnetzhou
 * @description 文件监视器,用来监控APP运行过程中被其篡改了文件信息而得到通知
 */
public class FileMonitor implements Runnable {
	
	private File entry;
	private ConcurrentHashMap<String,MonitorNode> monitorNodeMap;	//Map<path,MonitorNode>
	private final static long DEFAULT_MONITOR_PERIOD = 5000;			//默认5秒扫描一次
	private long period;
	private static TimeUnit tm = TimeUnit.MILLISECONDS;
	/**
	 * 定期监控调度器
	 */
	private ScheduledExecutorService scheduleExecutor;
	/**
	 * 监视节点状态改变通知器
	 */
	private MonitorNodeStateChangedNotifier monitorNodeStateChangedNotifier;
	private boolean isRunning = false;
	
	public static class MonitorNode {
		
		File file;
		String name;
		String parent;
		long size;
		long lastModified;
		boolean isDirectory;
		boolean isExists;
		List<MonitorNode> children;
		
		public MonitorNode(String path) {
			this(new File(path));
		}
		
		public MonitorNode(File file) {
			if(Objects.isNull(file) || !file.exists()) {
				throw new IllegalArgumentException("file must be exists!");
			}
			this.file = file;
			this.name = file.getName();
			this.parent = file.getParent();
			this.size = file.length();
			this.isDirectory = file.isDirectory();
			this.isExists = file.exists();
			this.lastModified = file.lastModified();
		}

		public File getFile() {
			return file;
		}

		public void setFile(File file) {
			this.file = file;
		}

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public String getParent() {
			return parent;
		}

		public void setParent(String parent) {
			this.parent = parent;
		}

		public long getSize() {
			return size;
		}

		public void setSize(long size) {
			this.size = size;
		}

		public long getLastModified() {
			return lastModified;
		}

		public void setLastModified(long lastModified) {
			this.lastModified = lastModified;
		}

		public boolean isDirectory() {
			return isDirectory;
		}

		public void setDirectory(boolean isDirectory) {
			this.isDirectory = isDirectory;
		}

		public boolean isExists() {
			return isExists;
		}

		public void setExists(boolean isExists) {
			this.isExists = isExists;
		}

		public List<MonitorNode> getChildren() {
			return children;
		}

		public void setChildren(List<MonitorNode> children) {
			this.children = children;
		}

		
		@Override
		public String toString() {
			// TODO Auto-generated method stub
			return JSON.toJSONString(this);
		}
	}
	
	
	public static interface MonitorNodeStateChangedNotifier {
		public void onUpdated(MonitorNode... node);
		public void onCreated(MonitorNode... node);
		public void onDeleted(String... deleteFiles);
	}
	
	public FileMonitor() {
		
	}
	
	public FileMonitor(String entry) {
		this(new File(entry));
	}
	
	public FileMonitor(File entry) {
		this(entry,DEFAULT_MONITOR_PERIOD,tm);
	}
	
	public FileMonitor(File entry,long period,TimeUnit tm) {
		this.entry = entry;
		this.period = period;
		this.tm = tm;
		this.monitorNodeMap = new ConcurrentHashMap<String,MonitorNode>();
	}
	
	public void setMonitorEntry(String entry) {
		if(Objects.nonNull(entry) && entry.length() == 0) {
			File entryFile = new File(entry);
			if(!entryFile.exists()) {
				throw new IllegalArgumentException("entry file must exists");
			} else {
				this.entry = entryFile;
				addFileUnderMonitor(entryFile);
			}
		} else {
			throw new IllegalArgumentException("invaild entry");
		}
	}
	
	/**
	 * 设置状态监测通知器
	 * @param monitorNodeStateChangedNotifier
	 */
	public void setMonitorNodeStateChangedNotifier(MonitorNodeStateChangedNotifier monitorNodeStateChangedNotifier) {
		this.monitorNodeStateChangedNotifier = monitorNodeStateChangedNotifier;
	}
	
	/**
	 * 开始监视
	 */
	public synchronized void start() {
		if(isRunning) {
			return;
		}
		if(Objects.isNull(scheduleExecutor) || scheduleExecutor.isShutdown()) {
			scheduleExecutor = Executors.newSingleThreadScheduledExecutor();
		}
		scheduleExecutor.scheduleAtFixedRate(this, 1000, period, tm);
	}
	
	/**
	 * 停止监视
	 */
	public synchronized void stop() {
		if(isRunning) {
			if(Objects.nonNull(scheduleExecutor) && !scheduleExecutor.isShutdown()) {
				scheduleExecutor.shutdownNow();
				scheduleExecutor = null;
			}
			isRunning = false;
		}
	}
	
	/**
	 *   把文件加入监控之中
	 * @param directory
	 */
	public void addFileUnderMonitor(String path) {
		addFileUnderMonitor(new File(path));
	}
	
	private void addFileUnderMonitor(File file) {
		if(Objects.isNull(file) || !file.exists()) {
			throw new IllegalArgumentException("file does not exists");
		}
		MonitorNode monitorNode = new MonitorNode(file);
		monitorNodeMap.put(file.getAbsolutePath(), monitorNode);
		if(file.isDirectory()) {
			doAddDirectoryWithChildren(monitorNode); 
		} 
	}
	
	public void removeFileFromMonitor(String path) {
		if(Objects.isNull(path) || path.length() == 0) {
			return;
		}
		monitorNodeMap.remove(path);
	}
	
	private void doAddDirectoryWithChildren(MonitorNode directoryNode) {
		File[] files = directoryNode.getFile().listFiles();
		if(Objects.nonNull(files) && files.length > 0) {
			ArrayList<MonitorNode> monitorNodes = new ArrayList<MonitorNode>(files.length);
			for(File file : files) {
				MonitorNode monitorNode = new MonitorNode(file);
				if(file.isDirectory()) {
					addFileUnderMonitor(file);
				}
				monitorNodes.add(monitorNode);
			}
		}
	}
	
	/**
	 * 对比文件是否已经变化
	 * @param node1
	 * @param node2
	 * @return
	 */
	private boolean compare(MonitorNode node1,MonitorNode node2) {
		boolean isDifferent = false;
		if(Objects.nonNull(node1) && Objects.nonNull(node2)) {
			isDifferent = !( node1.lastModified == node2.lastModified && node1.size == node2.size );
		}
		return isDifferent;
	}
	
	private List<File> getFilesOfEntry(File entry,List<File> files) {
		if(Objects.nonNull(entry) && entry.exists()) {
			files.add(entry);
			if(entry.isDirectory()) {
				File[] childFiles = entry.listFiles();
				if(Objects.nonNull(childFiles) && childFiles.length > 0) {
					for(File childFile : childFiles) {
						getFilesOfEntry(childFile,files);
					}
				}
			}
		}
		return files;
	}
	
	private void checkDeleted(File entry) {
		Set<Entry<String,MonitorNode>> entrySet = monitorNodeMap.entrySet();
		HashSet<String> monitorFileSet = new HashSet<String>();
		if(Objects.nonNull(entrySet) && entrySet.size() > 0) {
			List<File> files = new ArrayList<File>();
			getFilesOfEntry(entry,files);
			for(Entry<String,MonitorNode> e : entrySet) {
				monitorFileSet.add(e.getKey());
			}
			if(Objects.nonNull(files) && files.size() > 0) {
				for(File file : files) {
					monitorFileSet.remove(file.getAbsolutePath());
				}
			}
			
			if(monitorFileSet.size() > 0) {
				//监控列表中的文件在磁盘被删除了
				for(String monitorFilePath : monitorFileSet) {
					monitorNodeMap.remove(monitorFilePath);
				}
				if(monitorNodeStateChangedNotifier != null) {
					String[] deleteFiles = new String[monitorFileSet.size()];
					int index = 0;
					for(String fileName : monitorFileSet) {
						if(index < monitorFileSet.size()) {
							deleteFiles[index++] = fileName;
						} else {
							break;
						}
					}
					monitorNodeStateChangedNotifier.onDeleted(deleteFiles);
				}
			}
		}
	}
	
	private void checkUpdateOrCreate(File entry) {
		if(Objects.nonNull(entry) && entry.exists()) {
			MonitorNode monitorNode = new MonitorNode(entry);
			if(monitorNodeMap.containsKey(entry.getAbsolutePath())) {
				//已存在的
				MonitorNode origNode = monitorNodeMap.get(entry.getAbsolutePath());
				boolean isDifferent = compare(origNode,monitorNode);
				if(isDifferent) {
					monitorNodeMap.put(entry.getAbsolutePath(), monitorNode);
					if(monitorNodeStateChangedNotifier != null) {
						monitorNodeStateChangedNotifier.onUpdated(monitorNode);
					}
				}
			} else {
				//新增进来的
				monitorNodeMap.put(entry.getAbsolutePath(), monitorNode);
				if(monitorNodeStateChangedNotifier != null) {
					//通知监视器有新增的文件
					monitorNodeStateChangedNotifier.onCreated(monitorNode);
				}
			}
			if(entry.isDirectory()) {	//如果是目录再对子目录/文件迭代一次
				File[] childFiles = entry.listFiles();
				if(Objects.nonNull(childFiles) && childFiles.length > 0) {
					for(File childFile : childFiles) {
						checkUpdateOrCreate(childFile);
					}
				}
			}
		}
	}
	
	private void checkupAndReport() {
		checkUpdateOrCreate(entry);
		checkDeleted(entry);
	}
	
	@Override
	public void run() {
		// TODO Auto-generated method stub
		isRunning = true;
		try {
			checkupAndReport();
		}catch(Exception e) {
			e.printStackTrace();
		}
	}
	
	public static void main(String[] args) {
		String path = "E:\\workplace\\eclipse\\GitHelper\\test";
		FileMonitor fileMonitor = new FileMonitor(path);
		fileMonitor.addFileUnderMonitor(path);
		fileMonitor.setMonitorNodeStateChangedNotifier(new MonitorNodeStateChangedNotifier() {
			
			@Override
			public void onUpdated(MonitorNode... node) {
				// TODO Auto-generated method stub
				if(node != null && node.length > 0) {
					for(MonitorNode n :node) {
						System.out.println("文件:" + n.getFile().getAbsolutePath() + " 被修改");
					}
				}
			}
			
			@Override
			public void onDeleted(String... deleteFiles) {
				// TODO Auto-generated method stub
				if(deleteFiles != null && deleteFiles.length > 0) {
					for(String deleteFile :deleteFiles) {
						System.out.println("文件:" + deleteFile + " 被删除");
					}
				}
			}
			
			@Override
			public void onCreated(MonitorNode... node) {
				// TODO Auto-generated method stub
				if(node != null && node.length > 0) {
					for(MonitorNode n :node) {
						System.out.println("文件:" + n.getFile().getAbsolutePath() + " 被增加");
					}
				}
			}
		});
		fileMonitor.start();
	}

}
