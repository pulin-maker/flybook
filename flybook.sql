-- MySQL dump 10.13  Distrib 8.0.42, for Win64 (x86_64)
--
-- Host: localhost    Database: flybook
-- ------------------------------------------------------
-- Server version	8.0.42

CREATE DATABASE IF NOT EXISTS `flybook` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE `flybook`;

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `conversation_members`
--

DROP TABLE IF EXISTS `conversation_members`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `conversation_members` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `conversation_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `last_ack_seq` bigint DEFAULT '0' COMMENT '已确认同步到的序列号',
  `unread_count` int DEFAULT '0' COMMENT '未读消息数',
  `role` tinyint DEFAULT '1' COMMENT '角色: 1=成员, 2=管理员',
  `is_muted` tinyint DEFAULT '0' COMMENT '免打扰',
  `joined_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `is_top` tinyint(1) DEFAULT '0' COMMENT '是否置顶 0否 1是',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_conv_user` (`conversation_id`,`user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=36 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='会话成员表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `conversation_members`
--

LOCK TABLES `conversation_members` WRITE;
/*!40000 ALTER TABLE `conversation_members` DISABLE KEYS */;
INSERT INTO `conversation_members` VALUES (4,8,1001,0,0,1,0,'2025-11-27 06:44:44',1),(5,1,1002,0,0,0,0,'2025-11-27 06:45:43',0),(6,8,1002,0,10,0,0,'2025-11-27 06:46:51',0),(7,9,1001,0,0,1,0,'2025-11-27 06:55:28',1),(8,8,1003,0,8,0,0,'2025-11-27 07:43:08',0),(9,8,1004,0,7,0,0,'2025-11-27 07:50:28',0),(10,8,1005,0,6,0,0,'2025-11-27 07:55:31',0),(11,10,1001,0,0,1,0,'2025-12-09 14:19:52',0),(12,11,1001,0,0,1,0,'2025-12-09 15:05:13',0),(13,12,1001,0,0,1,0,'2025-12-09 15:05:50',0),(14,13,1001,0,0,1,0,'2025-12-09 15:05:55',0),(15,14,1001,0,0,1,0,'2025-12-09 15:05:58',0),(16,13,1002,0,2,0,0,'2025-12-09 15:33:12',0),(17,15,1002,0,0,1,0,'2025-12-09 15:39:06',0),(18,13,1003,0,1,0,0,'2025-12-09 15:39:59',0),(19,16,1005,0,0,1,0,'2025-12-09 15:41:17',0),(20,17,1005,0,0,1,0,'2025-12-09 15:41:51',0),(21,17,1006,0,1,0,0,'2025-12-09 15:42:11',0),(22,18,1005,0,0,1,0,'2025-12-09 15:44:18',0),(23,18,1006,0,1,0,0,'2025-12-09 15:44:33',0),(24,19,1005,0,0,1,0,'2025-12-09 15:46:50',0),(25,19,1006,0,1,0,0,'2025-12-09 15:47:17',0),(26,20,1005,0,0,1,0,'2025-12-09 15:51:17',0),(28,21,1005,0,0,1,0,'2025-12-09 15:59:35',0),(29,22,1005,0,0,1,0,'2025-12-09 15:59:45',0),(30,23,1005,0,0,1,0,'2025-12-09 15:59:47',0),(31,24,1005,0,0,1,0,'2025-12-09 16:03:11',0),(32,25,1005,0,0,1,0,'2025-12-09 16:03:13',0),(33,26,1006,0,0,1,0,'2025-12-09 16:03:55',0),(34,27,1001,0,0,1,0,'2025-12-10 01:43:24',0),(35,27,1005,0,0,0,0,'2025-12-10 01:47:41',0);
/*!40000 ALTER TABLE `conversation_members` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `conversations`
--

DROP TABLE IF EXISTS `conversations`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `conversations` (
  `conversation_id` bigint NOT NULL AUTO_INCREMENT,
  `type` tinyint NOT NULL DEFAULT '1' COMMENT '类型: 1=单聊(P2P), 2=群聊(Group)',
  `name` varchar(128) DEFAULT NULL COMMENT '群名称',
  `avatar_url` varchar(255) DEFAULT NULL COMMENT '群头像',
  `owner_id` bigint DEFAULT '0' COMMENT '群主ID',
  `current_seq` bigint DEFAULT '0' COMMENT '当前会话最新序列号',
  `last_msg_content` varchar(512) DEFAULT '' COMMENT '最新消息摘要',
  `last_msg_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最新消息时间',
  `created_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`conversation_id`)
) ENGINE=InnoDB AUTO_INCREMENT=28 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='会话表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `conversations`
--

LOCK TABLES `conversations` WRITE;
/*!40000 ALTER TABLE `conversations` DISABLE KEYS */;
INSERT INTO `conversations` VALUES (8,2,'Apifox 测试群',NULL,1001,10,'Hello World','2025-12-03 02:36:18','2025-11-27 06:44:44'),(9,2,'测试群组',NULL,1001,0,'','2025-11-27 06:55:28','2025-11-27 06:55:28'),(10,2,'测试群组3',NULL,1001,0,'','2025-12-09 14:19:51','2025-12-09 14:19:52'),(11,1,'测试群组',NULL,1001,0,'','2025-12-09 15:05:12','2025-12-09 15:05:13'),(12,1,'redis测试单聊',NULL,1001,0,'','2025-12-09 15:05:50','2025-12-09 15:05:50'),(13,1,'redis测试单聊1',NULL,1001,2,'ZhangSan 邀请 WangWu 加入了群聊','2025-12-09 15:39:59','2025-12-09 15:05:55'),(14,1,'redis测试单聊2',NULL,1001,0,'','2025-12-09 15:05:58','2025-12-09 15:05:58'),(15,1,'redis测试单聊2',NULL,1002,0,'','2025-12-09 15:39:06','2025-12-09 15:39:06'),(16,1,'redis测试单聊2',NULL,1005,0,'','2025-12-09 15:41:17','2025-12-09 15:41:17'),(17,1,'redis测试单聊2',NULL,1005,1,'beijing 邀请 nanjing 加入了群聊','2025-12-09 15:42:11','2025-12-09 15:41:51'),(18,1,'redis测试单聊10',NULL,1005,1,'beijing 邀请 nanjing 加入了群聊','2025-12-09 15:44:33','2025-12-09 15:44:18'),(19,1,'测试单聊100',NULL,1005,1,'beijing 邀请 nanjing 加入了群聊','2025-12-09 15:47:17','2025-12-09 15:46:50'),(20,1,'测试单聊200',NULL,1005,0,'','2025-12-09 15:51:17','2025-12-09 15:51:17'),(21,1,'测试单聊200',NULL,1005,0,'','2025-12-09 15:59:35','2025-12-09 15:59:35'),(22,1,'测试单聊200',NULL,1005,0,'','2025-12-09 15:59:44','2025-12-09 15:59:45'),(23,1,'测试单聊200',NULL,1005,0,'','2025-12-09 15:59:46','2025-12-09 15:59:47'),(24,1,'测试单聊200',NULL,1005,0,'','2025-12-09 16:03:11','2025-12-09 16:03:11'),(25,1,'测试单聊200',NULL,1005,0,'','2025-12-09 16:03:13','2025-12-09 16:03:13'),(26,1,'测试单聊300',NULL,1006,0,'','2025-12-09 16:03:54','2025-12-09 16:03:55'),(27,1,'测试群组a',NULL,1001,0,'','2025-12-10 01:43:24','2025-12-10 01:43:24');
/*!40000 ALTER TABLE `conversations` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `message_reactions`
--

DROP TABLE IF EXISTS `message_reactions`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `message_reactions` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `message_id` bigint NOT NULL COMMENT '关联消息ID',
  `user_id` bigint NOT NULL COMMENT '操作用户ID',
  `reaction_type` varchar(32) NOT NULL COMMENT '表情代码, 如: thumbsup, heart',
  `created_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_msg_user_react` (`message_id`,`user_id`,`reaction_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='消息表情回应表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `message_reactions`
--

LOCK TABLES `message_reactions` WRITE;
/*!40000 ALTER TABLE `message_reactions` DISABLE KEYS */;
/*!40000 ALTER TABLE `message_reactions` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `messages`
--

DROP TABLE IF EXISTS `messages`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `messages` (
  `message_id` bigint NOT NULL AUTO_INCREMENT COMMENT '全局唯一消息ID',
  `conversation_id` bigint NOT NULL,
  `sender_id` bigint NOT NULL,
  `seq` bigint NOT NULL COMMENT '会话内序列号',
  `quote_id` bigint DEFAULT '0' COMMENT '引用的消息ID (若不为0，则是回复消息)',
  `msg_type` tinyint NOT NULL COMMENT '1=Text, 2=Image, 3=Video, 4=File, 5=TodoCard, 6=RichPost',
  `content` json NOT NULL COMMENT '消息内容载体',
  `mentions` json DEFAULT NULL COMMENT '被@用户ID列表',
  `is_revoked` tinyint DEFAULT '0' COMMENT '是否已撤回',
  `created_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`message_id`),
  UNIQUE KEY `uk_conv_seq` (`conversation_id`,`seq`),
  KEY `idx_conv_time` (`conversation_id`,`created_time`)
) ENGINE=InnoDB AUTO_INCREMENT=30 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='消息存储表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `messages`
--

LOCK TABLES `messages` WRITE;
/*!40000 ALTER TABLE `messages` DISABLE KEYS */;
INSERT INTO `messages` VALUES (15,8,1001,1,0,1,'{\"text\": \"Hello LiSi, 这是来自 Apifox 的实时消息!\"}',NULL,0,'2025-11-27 06:48:22'),(16,8,1001,2,0,1,'{\"text\": \"Hello World\"}',NULL,0,'2025-11-27 06:57:34'),(17,8,1001,3,0,1,'{\"text\": \"ZhangSan 邀请  加入了群聊\"}',NULL,0,'2025-11-27 07:43:08'),(18,8,1001,4,0,1,'{\"text\": \"ZhangSan 邀请 nanjing 加入了群聊\"}',NULL,0,'2025-11-27 07:50:28'),(19,8,1001,5,0,1,'{\"text\": \"ZhangSan 邀请 beijing 加入了群聊\"}',NULL,0,'2025-11-27 07:55:31'),(20,8,1001,6,0,1,'{\"text\": \"你好，这是第一条测试消息\"}',NULL,0,'2025-11-28 09:15:44'),(21,8,1001,7,0,2,'{\"url\": \"http://localhost:8081/files/dfac8de2-3701-4161-bc61-215fb7575e7c.jpg\", \"width\": 500, \"height\": 300}',NULL,0,'2025-11-28 09:16:45'),(22,8,1001,8,0,5,'{\"title\": \"完成安卓大作业\", \"status\": 0, \"deadline\": \"2023-12-31\"}',NULL,0,'2025-11-28 09:17:18'),(23,8,1001,9,0,5,'{\"title\": \"完成安卓大作业\", \"status\": 0, \"deadline\": \"2023-12-31\"}',NULL,0,'2025-11-28 09:20:33'),(24,8,1001,10,0,1,'{\"text\": \"Hello World\"}',NULL,0,'2025-12-03 02:36:18'),(25,13,1001,1,0,1,'{\"text\": \"ZhangSan 邀请 LiSi 加入了群聊\"}',NULL,0,'2025-12-09 15:33:12'),(26,13,1001,2,0,1,'{\"text\": \"ZhangSan 邀请 WangWu 加入了群聊\"}',NULL,0,'2025-12-09 15:39:59'),(27,17,1005,1,0,1,'{\"text\": \"beijing 邀请 nanjing 加入了群聊\"}',NULL,0,'2025-12-09 15:42:11'),(28,18,1005,1,0,1,'{\"text\": \"beijing 邀请 nanjing 加入了群聊\"}',NULL,0,'2025-12-09 15:44:33'),(29,19,1005,1,0,1,'{\"text\": \"beijing 邀请 nanjing 加入了群聊\"}',NULL,0,'2025-12-09 15:47:17');
/*!40000 ALTER TABLE `messages` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `users`
--

DROP TABLE IF EXISTS `users`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `users` (
  `user_id` bigint NOT NULL AUTO_INCREMENT COMMENT '用户唯一ID',
  `username` varchar(64) NOT NULL COMMENT '用户名/姓名',
  `avatar_url` varchar(255) DEFAULT '' COMMENT '头像链接',
  `created_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `password` varchar(128) NOT NULL DEFAULT '123456' COMMENT '密码',
  PRIMARY KEY (`user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=1007 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户信息表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `users`
--

LOCK TABLES `users` WRITE;
/*!40000 ALTER TABLE `users` DISABLE KEYS */;
INSERT INTO `users` VALUES (1,'HutoolUser','http://avatar.com/2.jpg','2025-11-25 07:00:09','123456'),(2,'HutoolUser','http://avatar.com/2.jpg','2025-11-25 07:20:47','123456'),(1001,'ZhangSan','https://api.dicebear.com/7.x/avataaars/svg?seed=Zhang','2025-11-27 06:35:12','123456'),(1002,'LiSi','https://api.dicebear.com/7.x/avataaars/svg?seed=Li','2025-11-27 06:35:12','123456'),(1003,'WangWu','https://api.dicebear.com/7.x/avataaars/svg?seed=Wang','2025-11-27 07:44:13','123456'),(1004,'nanjing','https://api.dicebear.com/7.x/avataaars/svg?seed=nan','2025-11-27 07:49:46','123456'),(1005,'beijing','https://api.dicebear.com/7.x/avataaars/svg?seed=beijing','2025-11-27 07:55:07','123456'),(1006,'suzhou','https://api.dicebear.com/7.x/avataaars/svg?seed=beijing','2025-12-09 15:41:42','123456');
/*!40000 ALTER TABLE `users` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-03-16 12:14:31
