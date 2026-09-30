extends Node3D
## Real imported test asset. No visual primitive meshes or fabricated lottery machine.

var camera: Camera3D
var label: Label
var bridge: Object
var yaw := 0.35
var pitch := 0.05
var framing := 3.0
var centre := Vector3.ZERO

func _ready() -> void:
	if Engine.has_singleton("LotteryProbe"):
		bridge = Engine.get_singleton("LotteryProbe")
	var canvas := CanvasLayer.new()
	add_child(canvas)
	label = Label.new()
	label.position = Vector2(20, 52)
	label.size = Vector2(350, 150)
	label.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	label.add_theme_font_size_override("font_size", 16)
	label.add_theme_color_override("font_color", Color("353a39"))
	label.text = "引擎验证 · 非新版摇奖机\n正在导入测试资产…"
	canvas.add_child(label)
	var button := Button.new()
	button.text = "退出验证场景"
	button.position = Vector2(20, 730)
	button.size = Vector2(350, 48)
	button.pressed.connect(func(): get_tree().quit())
	canvas.add_child(button)
	var env := WorldEnvironment.new()
	env.environment = Environment.new()
	env.environment.background_mode = Environment.BG_COLOR
	env.environment.background_color = Color("e9e7e1")
	env.environment.ambient_light_source = Environment.AMBIENT_SOURCE_COLOR
	env.environment.ambient_light_color = Color("eee8dc")
	env.environment.ambient_light_energy = 0.65
	env.environment.tonemap_mode = Environment.TONE_MAPPER_FILMIC
	add_child(env)
	for direction in [Vector3(-0.6, -0.7, -0.6), Vector3(0.8, -0.3, 0.3)]:
		var light := DirectionalLight3D.new()
		light.light_energy = 1.5 if direction.x < 0 else 0.65
		add_child(light)
		light.look_at(direction)
	camera = Camera3D.new()
	camera.fov = 42
	add_child(camera)
	var doc := GLTFDocument.new()
	var state := GLTFState.new()
	var result := doc.append_from_file("res://fixture.glb", state)
	if result != OK:
		report("IMPORT_FAILED " + error_string(result))
		return
	var model := doc.generate_scene(state)
	if model == null:
		report("IMPORT_FAILED empty scene")
		return
	add_child(model)
	var bounds := collect_bounds(model)
	centre = bounds.get_center()
	var viewport_size: Vector2 = get_viewport().get_visible_rect().size
	var aspect: float = viewport_size.x / maxf(viewport_size.y, 1.0)
	var limiting_angle: float = atan(tan(deg_to_rad(camera.fov) * 0.5) * minf(aspect, 1.0))
	framing = max(bounds.size.length() * 0.55 / sin(limiting_angle), 0.1)
	update_camera()
	report("MODEL_READY Godot4.5.2 · Jolt · GLB/PBR\nDamagedHelmet / ctxwing / theblueturtle_\nCC-BY4 / 原版CC-BY-NC4\n拖动查看；不用于摇奖机交付。")
	run_contact_check()

func run_contact_check() -> void:
	# Invisible collision-only fixture; not a hand-made visual model.
	var floor_body := StaticBody3D.new()
	var floor_shape := CollisionShape3D.new()
	var box := BoxShape3D.new()
	box.size = Vector3(10, 1, 10)
	floor_shape.shape = box
	floor_body.add_child(floor_shape)
	add_child(floor_body)
	floor_body.position.y = -0.5
	var bodies: Array[RigidBody3D] = []
	for height in [2.0, 4.0]:
		var body := RigidBody3D.new()
		var shape := CollisionShape3D.new()
		var sphere := SphereShape3D.new()
		sphere.radius = 0.5
		shape.shape = sphere
		body.add_child(shape)
		body.continuous_cd = true
		add_child(body)
		body.position.y = height
		bodies.append(body)
	var passed := true
	for tick in 360:
		await get_tree().physics_frame
		for body in bodies:
			passed = passed and body.position.y >= 0.4
	for index in bodies.size():
		passed = passed and abs(bodies[index].position.y - (0.5 + index)) < 0.08 and bodies[index].linear_velocity.length() < 0.05
	var event := "%s engine=%s y=%.5f/%.5f" % ["CONTACT_OK" if passed else "CONTACT_FAILED", ProjectSettings.get_setting("physics/3d/physics_engine"), bodies[0].position.y, bodies[1].position.y]
	print("LOTTERY_PROBE " + event)
	if bridge != null:
		bridge.report_event(event)
	if "--auto-close" in OS.get_cmdline_user_args():
		get_tree().quit()

func collect_bounds(node: Node) -> AABB:
	var combined := AABB()
	var found := false
	for item in node.find_children("*", "MeshInstance3D", true, false):
		var mesh_node := item as MeshInstance3D
		var box := mesh_node.global_transform * mesh_node.get_aabb()
		combined = combined.merge(box) if found else box
		found = true
	return combined

func report(message: String) -> void:
	label.text = "引擎验证 · 非新版摇奖机\n" + message
	print("LOTTERY_PROBE " + message)
	if bridge != null:
		bridge.report_event(message)

func _unhandled_input(event: InputEvent) -> void:
	if event is InputEventScreenDrag:
		yaw -= event.relative.x * 0.006
		pitch = clamp(pitch + event.relative.y * 0.004, -0.45, 0.45)
		update_camera()
	if event.is_action_pressed("ui_cancel"):
		get_tree().quit()

func update_camera() -> void:
	camera.position = centre + Vector3(sin(yaw) * cos(pitch), sin(pitch), cos(yaw) * cos(pitch)) * framing
	camera.look_at(centre)
